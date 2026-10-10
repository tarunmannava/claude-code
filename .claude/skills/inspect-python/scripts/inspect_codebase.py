#!/usr/bin/env python3
"""
inspect_codebase.py - Deterministic Static AST Inspector
Part of the inspect-python skill.
Analyzes a Python repository without executing code.
"""

import ast
import json
import os
import sys
from pathlib import Path


class CodebaseVisitor(ast.NodeVisitor):
    def __init__(self, rel_path, mode="all"):
        self.rel_path = rel_path
        self.mode = mode
        self.imports = []
        self.classes = []
        self.functions = []
        self.dynamic_hazards = []
        self.scope_stack = []

    def visit_Import(self, node):
        if self.mode in ("all", "imports"):
            is_module_level = len(self.scope_stack) == 0
            for alias in node.names:
                self.imports.append({
                    "module": alias.name,
                    "name": None,
                    "alias": alias.asname,
                    "is_relative": False,
                    "level": 0,
                    "is_module_level": is_module_level,
                    "line": node.lineno
                })
        self.generic_visit(node)

    def visit_ImportFrom(self, node):
        if self.mode in ("all", "imports"):
            is_module_level = len(self.scope_stack) == 0
            for alias in node.names:
                self.imports.append({
                    "module": node.module or "",
                    "name": alias.name,
                    "alias": alias.asname,
                    "is_relative": node.level > 0,
                    "level": node.level,
                    "is_module_level": is_module_level,
                    "line": node.lineno
                })
        self.generic_visit(node)

    def visit_ClassDef(self, node):
        if self.mode in ("all", "types"):
            bases = [self._unparse(b) for b in node.bases]
            fields = []

            for item in node.body:
                if isinstance(item, ast.AnnAssign) and isinstance(item.target, ast.Name):
                    field_name = item.target.id
                    field_type = self._unparse(item.annotation)
                    has_default = item.value is not None
                    fields.append({
                        "name": field_name,
                        "type": field_type,
                        "has_default": has_default,
                        "line": item.lineno
                    })
                elif isinstance(item, ast.Assign):
                    for target in item.targets:
                        if isinstance(target, ast.Name):
                            fields.append({
                                "name": target.id,
                                "type": "Any",
                                "has_default": True,
                                "line": item.lineno
                            })

            self.classes.append({
                "name": node.name,
                "bases": bases,
                "fields": fields,
                "line": node.lineno
            })

        self.scope_stack.append(f"class:{node.name}")
        self.generic_visit(node)
        self.scope_stack.pop()

    def visit_FunctionDef(self, node):
        self._process_function(node)

    def visit_AsyncFunctionDef(self, node):
        self._process_function(node)

    def _process_function(self, node):
        if self.mode in ("all", "routes", "types"):
            decorators = [self._unparse(d) for d in node.decorator_list]
            params = []
            for arg in node.args.args:
                arg_name = arg.arg
                arg_type = self._unparse(arg.annotation) if arg.annotation else "Any"
                params.append({"name": arg_name, "type": arg_type})

            return_type = self._unparse(node.returns) if node.returns else "Any"

            route_info = None
            for dec in decorators:
                for verb in ["get", "post", "put", "delete", "patch", "options", "head"]:
                    if f".{verb}(" in dec or f"{verb}(" in dec:
                        route_info = {"verb": verb.upper(), "decorator": dec}
                        break

            if self.mode == "routes" and route_info is None:
                pass
            else:
                self.functions.append({
                    "name": node.name,
                    "decorators": decorators,
                    "route_info": route_info,
                    "parameters": params,
                    "return_type": return_type,
                    "is_method": len(self.scope_stack) > 0 and self.scope_stack[-1].startswith("class:"),
                    "line": node.lineno
                })

        self.scope_stack.append(f"fn:{node.name}")
        self.generic_visit(node)
        self.scope_stack.pop()

    def visit_Call(self, node):
        if self.mode in ("all", "hazards"):
            func_name = self._unparse(node.func)
            if func_name in ("getattr", "setattr", "eval", "exec", "globals", "locals"):
                self.dynamic_hazards.append({
                    "type": func_name,
                    "line": node.lineno,
                    "expression": func_name
                })
        self.generic_visit(node)

    def _unparse(self, node):
        if node is None:
            return ""
        try:
            return ast.unparse(node)
        except Exception:
            if isinstance(node, ast.Name):
                return node.id
            if isinstance(node, ast.Constant):
                return str(node.value)
            if isinstance(node, ast.Attribute):
                return f"{self._unparse(node.value)}.{node.attr}"
            return "<expr>"


def scan_directory(source_dir, mode="all"):
    source_path = Path(source_dir).resolve()
    results = []
    ignore_dirs = {".git", "__pycache__", ".venv", "venv", "env", ".pytest_cache", ".idea", ".vscode", "target"}

    for root, dirs, files in os.walk(source_path):
        dirs[:] = [d for d in dirs if d not in ignore_dirs]
        for f in files:
            if f.endswith(".py"):
                file_full = Path(root) / f
                rel_path = file_full.relative_to(source_path).as_posix()
                try:
                    code = file_full.read_text(encoding="utf-8")
                    tree = ast.parse(code, filename=rel_path)
                    visitor = CodebaseVisitor(rel_path, mode=mode)
                    visitor.visit(tree)

                    entry = {"relative_path": rel_path}
                    if mode in ("all", "imports"):
                        entry["imports"] = visitor.imports
                    if mode in ("all", "types"):
                        entry["classes"] = visitor.classes
                    if mode in ("all", "routes", "types"):
                        entry["functions"] = visitor.functions
                    if mode in ("all", "hazards"):
                        entry["dynamic_hazards"] = visitor.dynamic_hazards

                    results.append(entry)
                except Exception as e:
                    results.append({"relative_path": rel_path, "error": str(e)})

    results.sort(key=lambda r: r["relative_path"])
    return {"files": results, "mode": mode}


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"error": "Usage: inspect_codebase.py <source_dir> [--mode all|imports|types|routes|hazards]"}))
        sys.exit(1)

    source_dir = sys.argv[1]
    mode = "all"
    for i in range(2, len(sys.argv)):
        if sys.argv[i] == "--mode" and i + 1 < len(sys.argv):
            mode = sys.argv[i + 1]

    profile = scan_directory(source_dir, mode=mode)
    print(json.dumps(profile, indent=2))
