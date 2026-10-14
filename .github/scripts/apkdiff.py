#!/usr/bin/env python3
"""Compare two APKs entry by entry, ignoring v1 signature files.

Exit 0 when every entry (name, CRC, size) matches, 1 otherwise. APK Signature
Scheme v2/v3 data lives outside the zip entries, so a signed and an unsigned
build of the same code compare equal here. Use apksigcopier for the byte-exact
check that F-Droid runs.
"""
import sys
import zipfile

SIG_SUFFIXES = (".SF", ".RSA", ".DSA", ".EC")


def entries(path):
    with zipfile.ZipFile(path) as z:
        out = {}
        for i in z.infolist():
            name = i.filename
            if name.startswith("META-INF/") and (name.endswith(SIG_SUFFIXES) or name == "META-INF/MANIFEST.MF"):
                continue
            out[name] = (i.CRC, i.file_size)
        return out


def main(a, b):
    ea, eb = entries(a), entries(b)
    diff = 0
    for name in sorted(set(ea) | set(eb)):
        if name not in ea:
            print(f"only in B: {name}")
        elif name not in eb:
            print(f"only in A: {name}")
        elif ea[name] != eb[name]:
            print(f"differs:   {name}  A crc={ea[name][0]:08x} size={ea[name][1]}  B crc={eb[name][0]:08x} size={eb[name][1]}")
        else:
            continue
        diff += 1
    print(f"{len(ea)} entries in A, {len(eb)} in B, {diff} differ")
    return 1 if diff else 0


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit("usage: apkdiff.py A.apk B.apk")
    sys.exit(main(sys.argv[1], sys.argv[2]))
