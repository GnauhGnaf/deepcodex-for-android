#!/usr/bin/env python3
"""
Build pre-installed Alpine rootfs for Codeeps app.
Downloads all Alpine + Python packages from Tsinghua mirror,
extracts into rootfs, creates final tarballs.
"""

import argparse
import gzip
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import urllib.request
import zipfile
from pathlib import Path

# ── Config ──────────────────────────────────────────────
ALPINE_VERSION = "v3.24"
ALPINE_MIRROR = f"https://mirrors.tuna.tsinghua.edu.cn/alpine/{ALPINE_VERSION}"
PIP_INDEX = "https://pypi.tuna.tsinghua.edu.cn/simple"

# System packages (Alpine apk)
SYSTEM_PACKAGES = [
    # ── Base system ──
    "python3",
    "py3-pip",
    "curl",
    "pandoc-cli",
    "poppler-utils",
    "font-wqy-zenhei",
    "font-noto-cjk",
    # ── Python: UI & CLI ──
    "py3-rich",
    "py3-click",
    "py3-colorama",
    "py3-termcolor",
    "py3-tabulate",
    # ── Python: HTTP & network ──
    "py3-requests",
    "py3-httpx",
    "py3-aiohttp",
    "py3-pysocks",
    "py3-tornado",
    # ── Python: data formats & documents ──
    "py3-pillow",
    "py3-lxml",
    "py3-defusedxml",
    "py3-beautifulsoup4",
    "py3-openpyxl",
    "py3-xlsxwriter",
    "py3-pypdf",
    "py3-pdfminer",
    "py3-markdown",
    "py3-docutils",
    "py3-yaml",
    "py3-jinja2",
    "py3-mako",
    # ── Python: data science ──
    "py3-numpy",
    "py3-pandas",
    "py3-scipy",
    "py3-matplotlib",
    "py3-scikit-learn",
    "py3-networkx",
    "py3-sympy",
    # ── Python: validation & serialization ──
    "py3-pydantic",
    "py3-cryptography",
    "py3-pycryptodome",
    "py3-xxhash",
    # ── Python: utilities ──
    "py3-tqdm",
    "py3-psutil",
    "py3-arrow",
    "py3-protobuf",
    # ── LibreOffice (headless document conversion) ──
    "libreoffice-writer",
    "libreoffice-impress",
    "libreoffice-calc",
]

# Pure Python packages (no C extensions) — download .whl, extract into site-packages
PIP_PURE_PACKAGES = [
    "python-pptx",
    "python-docx",
    "markitdown",
    "deepseek-ocr",
]

TMPDIR = Path("f:/移动os/app/tmp_build")


def download(url, dest_path, timeout=120):
    """Download with caching and progress."""
    dest_path = Path(dest_path)
    dest_path.parent.mkdir(parents=True, exist_ok=True)
    if dest_path.exists():
        return
    print(f"    GET {Path(url).name}")
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Codeeps-Builder/1.0"})
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            dest_path.write_bytes(resp.read())
    except Exception as e:
        print(f"    ERROR: {e}")
        raise


def fetch_apkindex(arch, repo):
    """Download and parse an APKINDEX file."""
    url = f"{ALPINE_MIRROR}/{repo}/{arch}/APKINDEX.tar.gz"
    req = urllib.request.Request(url, headers={"User-Agent": "Codeeps-Builder"})
    data = urllib.request.urlopen(req, timeout=30).read()
    with tarfile.open(fileobj=io.BytesIO(data), mode='r:gz') as tf:
        for member in tf.getmembers():
            if member.name == "APKINDEX":
                text = tf.extractfile(member).read().decode('utf-8', errors='replace')
                break
    packages = {}
    current = {}
    for line in text.splitlines():
        if line.strip() == '':
            if current and 'P' in current:
                current['_repo'] = repo
                packages[current['P']] = current
            current = {}
        elif len(line) > 1 and line[1] == ':':
            current[line[0]] = line[2:]
    if current and 'P' in current:
        current['_repo'] = repo
        packages[current['P']] = current
    return packages


def resolve_deps(wanted_list, index, provide_map):
    """Resolve full dependency tree for a list of top-level packages."""
    resolved = set()

    def resolve(name):
        if name in resolved:
            return
        p = index.get(name)
        if not p:
            return
        resolved.add(name)
        deps_str = p.get('D', '')
        for dep in deps_str.split():
            dep_clean = re.split(r'[<>=!~]', dep)[0]
            if dep.startswith('so:') or dep.startswith('cmd:'):
                provider = provide_map.get(dep_clean)
                if provider and provider not in resolved:
                    resolve(provider)
            elif dep_clean and dep_clean not in resolved:
                resolve(dep_clean)

    for w in wanted_list:
        resolve(w)
    return resolved


def build_provide_map(index):
    """Build a map from soname/cmd provides to package names."""
    pmap = {}
    for name, p in index.items():
        if 'p' in p:
            for prov in p['p'].split():
                prov_clean = prov.split('=')[0]
                if prov_clean not in pmap:
                    pmap[prov_clean] = name
    return pmap


def extract_apk(apk_path, rootfs_dir, symlinks_list):
    """Extract Alpine .apk into rootfs directory.
    Symlinks are NOT created on Windows — they're recorded in symlinks_list
    for later creation by Android's Os.symlink() via TarExtractor.
    """
    rootfs_dir = Path(rootfs_dir)
    with gzip.open(apk_path, 'rb') as gz:
        raw = gz.read()

    tmp_dir = TMPDIR / "extract_tmp"
    tmp_dir.mkdir(parents=True, exist_ok=True)
    tmp_path = tmp_dir / f"{Path(apk_path).stem}.tar"
    tmp_path.write_bytes(raw)
    try:
        with tarfile.open(tmp_path, 'r') as tf:
            for member in tf.getmembers():
                name = member.name.lstrip('./')
                if not name or name in ('.PKGINFO', '.INSTALL', '.SIGN.RSA.*'):
                    continue
                dst = rootfs_dir / name
                if member.isdir():
                    dst.mkdir(parents=True, exist_ok=True)
                elif member.issym():
                    # Record symlink for Android TarExtractor, don't create on Windows
                    dst.parent.mkdir(parents=True, exist_ok=True)
                    link_rel = str(dst.relative_to(rootfs_dir)).replace("\\", "/")
                    symlinks_list.append(f"{link_rel}|{member.linkname}")
                elif member.isreg():
                    dst.parent.mkdir(parents=True, exist_ok=True)
                    try:
                        f = tf.extractfile(member)
                        if f:
                            dst.write_bytes(f.read())
                    except (OSError, KeyError):
                        pass
                elif member.islnk():
                    # Hard link — copy the content
                    link_target = rootfs_dir / member.linkname.lstrip('./')
                    dst.parent.mkdir(parents=True, exist_ok=True)
                    if link_target.exists():
                        dst.write_bytes(link_target.read_bytes())
    finally:
        try:
            tmp_path.unlink()
        except OSError:
            pass


def install_pure_python_pkg(pkg_name, site_packages, cache_dir):
    """Download a pure-Python .whl from PyPI and extract to site-packages.
    No Python execution needed — just unzip the .whl (which is a zip file)."""
    pkg_clean = pkg_name.replace("[pptx]", "")

    # Query PyPI JSON API for package info
    pypi_url = f"https://pypi.org/pypi/{pkg_clean}/json"
    try:
        req = urllib.request.Request(pypi_url, headers={"User-Agent": "Codeeps-Builder"})
        data = json.loads(urllib.request.urlopen(req, timeout=30).read())
    except Exception as e:
        print(f"    WARNING: Cannot query PyPI for {pkg_name}: {e}")
        return False

    # Find a pure-python wheel (py3-none-any), fallback to any wheel
    wheels = [u for u in data.get("urls", []) if u["packagetype"] == "bdist_wheel"]
    pure = [w for w in wheels if "py3-none-any" in w["filename"]]
    chosen = pure[0] if pure else (wheels[0] if wheels else None)

    if not chosen:
        print(f"    WARNING: No wheel for {pkg_name}, skipping")
        return False

    # Download to cache
    wheel_path = cache_dir / chosen["filename"]
    if not wheel_path.exists():
        download(chosen["url"], wheel_path)

    # The .dist-info dir for this package
    dist_info = None

    # Extract .whl (zip format) to site-packages
    with zipfile.ZipFile(wheel_path, 'r') as zf:
        for name in zf.namelist():
            parts = name.split('/')
            if not parts[0]:
                continue

            if parts[0].endswith('.dist-info'):
                # Record dist-info dir name for verification
                if dist_info is None:
                    dist_info = parts[0]
                out_path = site_packages / name
                if name.endswith('/'):
                    out_path.mkdir(parents=True, exist_ok=True)
                else:
                    out_path.parent.mkdir(parents=True, exist_ok=True)
                    out_path.write_bytes(zf.read(name))
            elif parts[0].endswith('.data'):
                # .data contains scripts/data files — skip for simplicity
                pass
            elif name.endswith('.pyc') or name.endswith('/'):
                continue
            else:
                # Module/package directory or .py file
                out_path = site_packages / name
                out_path.parent.mkdir(parents=True, exist_ok=True)
                out_path.write_bytes(zf.read(name))

    print(f"    OK {pkg_name} ({chosen['filename']})")
    return True


def build_rootfs(arch):
    """Build a complete rootfs tarball for one architecture."""
    work_dir = TMPDIR / f"build_{arch}"
    apk_cache = TMPDIR / f"apk_{arch}"
    wheel_cache = TMPDIR / f"wheels_{arch}"
    rootfs_dir = work_dir / "rootfs"

    # Clean
    if work_dir.exists():
        shutil.rmtree(work_dir)
    apk_cache.mkdir(parents=True, exist_ok=True)
    wheel_cache.mkdir(parents=True, exist_ok=True)

    # ── Step 1: Extract base rootfs ──
    # Always download fresh Alpine minirootfs to tmp (don't use previous build output)
    base_tarball = TMPDIR / f"alpine-base-{arch}.tar.gz"
    alpine_url = f"https://mirrors.tuna.tsinghua.edu.cn/alpine/v3.24/releases/{arch}/alpine-minirootfs-3.24.0-{arch}.tar.gz"

    if not base_tarball.exists():
        print(f"  Downloading Alpine base from {alpine_url}...")
        req = urllib.request.Request(alpine_url, headers={"User-Agent": "Codeeps-Builder"})
        data = urllib.request.urlopen(req, timeout=120).read()
        base_tarball.write_bytes(data)
        print(f"  Downloaded: {len(data) // 1024 // 1024} MB")
    else:
        print(f"  Using cached Alpine base: {base_tarball.stat().st_size // 1024 // 1024} MB")

    print(f"\n{'='*60}")
    print(f"Building rootfs for {arch}")
    print(f"Base: {base_tarball} ({base_tarball.stat().st_size // 1024 // 1024} MB)")
    print(f"{'='*60}")

    print("\n[1/5] Extracting base rootfs...")
    rootfs_dir.mkdir(parents=True)
    all_symlinks = []  # Collect symlinks from base + apk packages

    # Manual extraction to record symlinks (Windows can't create them)
    with tarfile.open(base_tarball, 'r:gz') as tf:
        for member in tf.getmembers():
            name = member.name.lstrip('./')
            if not name:
                continue
            dst = rootfs_dir / name
            if member.isdir():
                dst.mkdir(parents=True, exist_ok=True)
            elif member.issym():
                dst.parent.mkdir(parents=True, exist_ok=True)
                link_rel = str(dst.relative_to(rootfs_dir)).replace("\\", "/")
                all_symlinks.append(f"{link_rel}|{member.linkname}")
            elif member.isreg():
                dst.parent.mkdir(parents=True, exist_ok=True)
                try:
                    f = tf.extractfile(member)
                    if f:
                        dst.write_bytes(f.read())
                except (OSError, KeyError):
                    pass
            elif member.islnk():
                link_target = rootfs_dir / member.linkname.lstrip('./')
                dst.parent.mkdir(parents=True, exist_ok=True)
                if link_target.exists():
                    dst.write_bytes(link_target.read_bytes())
    base_count = len(list(rootfs_dir.rglob('*')))
    print(f"  {base_count} entries extracted, {len(all_symlinks)} symlinks recorded")

    # Remember which files already exist (to exclude from .apk extraction)
    existing_files = set()
    for f in rootfs_dir.rglob('*'):
        if f.is_file():
            existing_files.add(str(f.relative_to(rootfs_dir)).replace("\\", "/"))

    # ── Step 2: Resolve and download Alpine packages ──
    print("\n[2/5] Resolving Alpine packages...")
    main_idx = fetch_apkindex(arch, "main")
    comm_idx = fetch_apkindex(arch, "community")
    index = {**main_idx, **comm_idx}
    provide_map = build_provide_map(index)
    print(f"  Index: {len(main_idx)} main + {len(comm_idx)} community = {len(index)} total")

    all_pkgs = resolve_deps(SYSTEM_PACKAGES, index, provide_map)
    print(f"  Need {len(all_pkgs)} packages (with deps)")

    # All resolved packages are needed — let resolve_deps handle the tree
    needed = set(all_pkgs)

    print(f"\n[3/5] Downloading {len(needed)} Alpine packages...")
    total_size = 0
    downloaded = []
    for pkg_name in sorted(needed):
        p = index[pkg_name]
        repo = p.get('_repo', 'main')
        filename = f"{pkg_name}-{p['V']}.apk"
        url = f"{ALPINE_MIRROR}/{repo}/{arch}/{filename}"
        apk_path = apk_cache / filename
        try:
            download(url, apk_path)
            downloaded.append(apk_path)
            total_size += apk_path.stat().st_size
        except Exception as e:
            print(f"    FAILED {filename}: {e}")
            # Try other repo
            other_repo = "community" if repo == "main" else "main"
            try:
                url2 = f"{ALPINE_MIRROR}/{other_repo}/{arch}/{filename}"
                download(url2, apk_path)
                downloaded.append(apk_path)
                total_size += apk_path.stat().st_size
            except Exception:
                print(f"    SKIPPING {pkg_name}")

    print(f"  Downloaded {len(downloaded)} packages ({total_size // 1024 // 1024} MB)")

    # ── Step 4: Extract packages into rootfs ──
    print(f"\n[4/5] Extracting packages into rootfs...")
    for i, apk_path in enumerate(downloaded):
        if i % 20 == 0:
            print(f"  {i+1}/{len(downloaded)}...")
        try:
            extract_apk(apk_path, rootfs_dir, all_symlinks)
        except Exception as e:
            print(f"    ERROR: {apk_path.name}: {e}")
    print(f"  {len(downloaded)} packages extracted, {len(all_symlinks)} symlinks recorded")

    # ── Step 5: Configure system + symlinks ──
    print(f"\n[5/5] Configuring system...")

    # Alpine repos (Tsinghua)
    (rootfs_dir / "etc/apk/repositories").parent.mkdir(parents=True, exist_ok=True)
    (rootfs_dir / "etc/apk/repositories").write_text(
        f"{ALPINE_MIRROR}/main\n{ALPINE_MIRROR}/community\n"
    )

    # DNS
    (rootfs_dir / "etc/resolv.conf").write_text(
        "nameserver 8.8.8.8\nnameserver 8.8.4.4\n"
    )

    # pip config with Tsinghua mirror
    pip_dir = rootfs_dir / "root/.config/pip"
    pip_dir.mkdir(parents=True, exist_ok=True)
    (pip_dir / "pip.conf").write_text(
        "[global]\n"
        "break-system-packages = true\n"
        "index-url = https://pypi.tuna.tsinghua.edu.cn/simple\n"
        "trusted-host = pypi.tuna.tsinghua.edu.cn\n"
    )

    # Workspace directory
    (rootfs_dir / "workspace").mkdir(exist_ok=True)

    # Pre-install pure Python packages from .whl (no network needed on phone)
    if PIP_PURE_PACKAGES:
        print("\n[5a/5] Installing pure Python packages from .whl...")
        site_packages = rootfs_dir / "usr/lib/python3.14/site-packages"
        site_packages.mkdir(parents=True, exist_ok=True)
        whl_cache = TMPDIR / f"whl_{arch}"
        whl_cache.mkdir(parents=True, exist_ok=True)
        for pkg in PIP_PURE_PACKAGES:
            install_pure_python_pkg(pkg, site_packages, whl_cache)
        print(f"  {len(PIP_PURE_PACKAGES)} Python packages pre-installed")

    # Write symlinks.list for Android TarExtractor
    symlinks_file = rootfs_dir / "symlinks.list"
    symlinks_file.write_text("\n".join(all_symlinks))
    print(f"  {len(all_symlinks)} symlinks → symlinks.list")

    # LibreOffice pre-installed marker (if soffice exists)
    if (rootfs_dir / "usr/lib/libreoffice/program/soffice").exists():
        print("  LibreOffice detected in rootfs")

    print(f"  Configured: Alpine mirror, pip mirror, resolv.conf, symlinks.list")

    # ── Package final tarball ──
    print(f"\nPackaging final tarball...")
    output = Path("app/src/main/assets/rootfs") / f"alpine-minirootfs-{arch}.tar"

    # Back up existing if valid
    if output.exists() and output.stat().st_size > 10000:
        bak = Path(str(output) + ".bak")
        shutil.copy2(output, bak)
        print(f"  Backed up existing to {bak.name}")

    total_entries = 0
    with tarfile.open(output, 'w:gz') as tf:
        for item in sorted(rootfs_dir.rglob('*')):
            arcname = str(item.relative_to(rootfs_dir)).replace("\\", "/")
            if arcname == '.':
                continue
            try:
                tf.add(str(item), arcname=arcname, recursive=False)
                total_entries += 1
            except (OSError, tarfile.TarError):
                pass

    size_mb = output.stat().st_size / (1024 * 1024)
    print(f"  Built: {output} ({size_mb:.1f} MB, {total_entries} entries)")

    # Cleanup
    def on_rm_error(func, path, exc_info):
        try:
            os.chmod(path, 0o777)
            func(path)
        except Exception:
            pass
    shutil.rmtree(work_dir, onerror=on_rm_error)
    try:
        shutil.rmtree(TMPDIR / "extract_tmp", onerror=on_rm_error)
    except Exception:
        pass

    return output


def main():
    parser = argparse.ArgumentParser(description="Build pre-installed Alpine rootfs")
    parser.add_argument("--arch", choices=["aarch64", "x86_64"], default="aarch64")
    parser.add_argument("--all", action="store_true", help="Build both architectures")
    args = parser.parse_args()

    if args.all:
        for a in ["aarch64", "x86_64"]:
            build_rootfs(a)
    else:
        build_rootfs(args.arch)


if __name__ == "__main__":
    main()
