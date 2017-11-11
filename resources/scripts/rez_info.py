#!/bin/env python
"""
Utility to get information about a rez package.
It will print a json formated dict to stdout, that can be used in other applications.

Author: Fredrik Braennbacka (fredrik.brannbacka@ilpvfx.com)
"""
import json
import sys
import os
from rez.packages_ import get_developer_package
from rez.resolved_context import ResolvedContext
import argparse


def get_dependencies(context):
    """
    Get a dict of python dependencies for the provided context.

    :param context: A resolved context
    :return: A dict with library names and their corresponding path
    """
    result = {}
    for path in context.get_environ()["PYTHONPATH"].split(':'):
        matches = list(filter(lambda x: x.root in path, context.resolved_packages))
        if not matches:
            continue
        package = matches[0]
        result[package.qualified_package_name] = path
    return result


def get_package_info(path, variant_index):
    """
    Get valuable information about a package that can be used in different contexts.

    :param path: Path to the root of a project
    :param variant: index of the variant to resolve
    :return: Dict with various info about a package
    """
    data = {
        "variants": [],
    }
    package = get_developer_package(path)
    variants = list(package.iter_variants())
    for v in variants:
        data["variants"].append(v.qualified_name)
    variant = variants[variant_index]
    request = variant.get_requires(build_requires=True, private_build_requires=True)
    context = ResolvedContext(request)
    data["name"] = package.name
    data["interpreter"] = context.which("python")
    data["dependencies"] = get_dependencies(context)
    return data


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Rez info.')
    parser.add_argument('root', default=os.getcwd(), help="path to the root of package")
    parser.add_argument('--variant', type=int, default=0, help="variant to resolve")
    args = parser.parse_args()
    data = get_package_info(args.root, args.variant)
    print json.dumps(data, indent=2)
