#!/usr/bin/env python3
"""Copies the latest web app into the Android assets.
Run this after changing index.html, app.js, styles.css or local-api.js in the web project.
Usage: python3 sync-web.py /path/to/bucketlist"""
import os, shutil, sys

web = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "bucketlist")
assets = os.path.join(os.path.dirname(os.path.abspath(__file__)), "app", "src", "main", "assets")
files = ["index.html", "app.js", "styles.css", "local-api.js", "manifest.webmanifest"]

os.makedirs(os.path.join(assets, "icons"), exist_ok=True)
for name in files:
    shutil.copy2(os.path.join(web, name), os.path.join(assets, name))
for icon in os.listdir(os.path.join(web, "icons")):
    shutil.copy2(os.path.join(web, "icons", icon), os.path.join(assets, "icons", icon))
print("Synced", len(files), "files and icons into", os.path.normpath(assets))
