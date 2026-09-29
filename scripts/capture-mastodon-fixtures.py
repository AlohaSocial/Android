#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Aloha Social contributors
# SPDX-License-Identifier: MIT
"""Capture mastodon.social's public discovery documents into the fixture corpus.

Only documents without personal data are fetched. The instance's contact account is
replaced by a fictional one, so no real person's profile lands in the repository.
Run from the repository root: python3 scripts/capture-mastodon-fixtures.py
"""

import json
import pathlib
import urllib.request

HOST = "https://mastodon.social"
KEEP_HEADERS = {"content-type", "link", "cache-control", "etag"}
ROUTES = {
    "instance-v1": "/api/v1/instance",
    "instance-v2": "/api/v2/instance",
    "nodeinfo-links": "/.well-known/nodeinfo",
    "nodeinfo-2.0": "/nodeinfo/2.0",
    "oauth-authorization-server": "/.well-known/oauth-authorization-server",
}
FICTIONAL_CONTACT = {
    "id": "1",
    "username": "admin",
    "acct": "admin",
    "display_name": "Instance admin",
    "locked": False,
    "bot": False,
    "created_at": "2016-03-16T00:00:00.000Z",
    "note": "",
    "url": "https://mastodon.social/@admin",
    "avatar": "",
    "avatar_static": "",
    "header": "",
    "header_static": "",
    "followers_count": 0,
    "following_count": 0,
    "statuses_count": 0,
    "fields": [],
    "emojis": [],
}


def fetch(path):
    request = urllib.request.Request(HOST + path, headers={"Accept": "application/json"})
    with urllib.request.urlopen(request, timeout=20) as response:
        headers = {k.lower(): v for k, v in response.headers.items() if k.lower() in KEEP_HEADERS}
        return response.status, headers, json.loads(response.read().decode("utf-8"))


def scrub(body):
    if isinstance(body, dict):
        if "contact_account" in body:
            body["contact_account"] = FICTIONAL_CONTACT
        contact = body.get("contact")
        if isinstance(contact, dict) and "account" in contact:
            contact["account"] = FICTIONAL_CONTACT
            contact["email"] = "admin@example.invalid"
        if "email" in body:
            body["email"] = "admin@example.invalid"
    return body


def main():
    version = scrub(fetch("/api/v2/instance")[2])["version"].split()[0].split("+")[0]
    target = pathlib.Path("core/testing/src/main/resources/fixtures") / f"mastodon-social-{version}"
    target.mkdir(parents=True, exist_ok=True)
    for name, path in ROUTES.items():
        status, headers, body = fetch(path)
        fixture = {"request": {"method": "GET", "path": path}, "status": status, "headers": headers, "body": scrub(body)}
        (target / f"{name}.json").write_text(json.dumps(fixture, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"{name}: {status}")


if __name__ == "__main__":
    main()
