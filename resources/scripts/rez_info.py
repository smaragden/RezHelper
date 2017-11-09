#!/bin/env python

import json
import sys
from rez.packages_ import get_developer_package
from rez.resolved_context import ResolvedContext


def get_dependency_info(context):
    result = []
    for path in context.get_environ()["PYTHONPATH"].split(':'):
        matches = list(filter(lambda x: x.root in path, context.resolved_packages))
        if not matches:
            continue

        package = matches[0]
        result.append({package.qualified_package_name: path})
    return result


def get_package_info(path):
    data = {}
    package = get_developer_package(path)
    variant = package.get_variant(0)
    request = variant.get_requires(build_requires=True, private_build_requires=True)
    context = ResolvedContext(request)
    data["name"] = package.name
    data["interpreter"] = context.which("python")
    data["dependencies"] = get_dependency_info(context)
    return data


if __name__ == "__main__":
    project_root = sys.argv[1]
    data = get_package_info(project_root)
    print json.dumps(data, indent=2)
