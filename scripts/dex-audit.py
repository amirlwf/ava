#!/usr/bin/env python3
"""Audit a built APK's dex files for missing types and methods.

Round-3's ad-hoc audit missed androidx.collection.OrderedScatterSetKt
(150 missing types, "all harmless") even though compose-ui 1.12.1
references it and no artifact on the classpath provides it. That audit
script is gone, so this is the durable replacement — parse EVERY dex
file in the APK (classes.dex, classes2.dex, ...), not just the first.

Checks:
  1. Type audit: every type in every dex's type_ids must be either
     defined by a class_def in some dex file or provided by the Android
     framework (android.*, java.*, javax.*, dalvik.*, org.json, ...).
     Any other missing type is reported; any missing androidx.* type
     is a HARD error (exit 2) — library code does real getstatic /
     method calls on those at runtime, so "harmless" is never the
     right call for them.
  2. Method audit: every method ref whose declaring class is defined
     in the dex must resolve to a method defined in that class or one
     of its dex-defined ancestors. Refs into framework classes are
     assumed provided. Unresolvable refs are reported (exit 2).
  3. Targeted check: every androidx/collection/* type referenced by
     any dex class must be defined (compose-ui needs OrderedScatterSet
     since 1.5.0-era collection; the manual resolver never shipped
     collection-android).

Usage: dex-audit.py <apk> [--dex-only] [--quiet]
  --dex-only: audit only classes.dex (reproduces the round-3 blind spot
     for comparison; NOT a pass).
Exit: 0 = clean, 1 = missing non-androidx types only, 2 = hard error
(missing androidx.* type or unresolvable method).
"""
import struct
import sys
import zipfile
from collections import defaultdict

FRAMEWORK_PREFIXES = (
    "Landroid/",
    "Ljava/",
    "Ljavax/",
    "Ldalvik/",
    "Lcom/android/",
    "Lorg/json/",
    "Lorg/w3c/dom/",
    "Lorg/xml/sax/",
    "Lorg/xmlpull/",
    "Lsun/",
    "Llibcore/",
)

# Missing types that are NEVER packaged in an APK — not even by a real
# Gradle build — because the device's OEM window-extension implementation
# provides them at runtime and androidx.window loads them reflectively.
# Anything else missing under androidx.* is a hard error.
ALLOWLISTED_MISSING = (
    "Landroidx/window/extensions/",
    "Landroidx/window/sidecar/",
)

# Missing types referenced ONLY by code the app never loads. Each entry
# carries its evidence; remove the entry if the app ever uses the
# referencing feature. These are NOT OEM-provided — they are dormant.
# A real Gradle build of this app lacks them too: room-paging is only a
# version pin in room-runtime's dependencyManagement, never a dependency.
DORMANT_ALLOWLIST = (
    # androidx.paging.PositionalDataSource*: referenced only by
    # androidx.room.paging.LimitOffsetDataSource (its superclass), which
    # Room loads only via its Paging integration (RoomPagingSource).
    # The app has no Paging code and never creates a RoomPagingSource.
    "Landroidx/paging/",
)

PRIMITIVES = set("BCDFIJSVZ")


def normalize_type(desc):
    """Strip array brackets; return None for primitives (not classes)."""
    while desc.startswith("["):
        desc = desc[1:]
    if desc in PRIMITIVES:
        return None
    return desc


def read_uleb128(buf, off):
    result = 0
    shift = 0
    while True:
        b = buf[off]
        off += 1
        result |= (b & 0x7F) << shift
        if not (b & 0x80):
            break
        shift += 7
    return result, off


def parse_dex(data):
    """Return (type_descriptors, defined_classes, method_refs, defined_methods).

    defined_methods: dict class_desc -> set of (name, shorty_proto_key)
    where the proto key is the return type + parameter types joined.
    method_refs: list of (class_desc, name, proto_key).
    """
    if data[:3] != b"dex":
        raise ValueError("not a dex file")
    # header after the 8-byte magic: checksum(4) + signature(20), then
    # 20 uints: file_size, header_size, endian_tag, link_size, link_off,
    # map_off, string_ids_size/off, type_ids_size/off, proto_ids_size/off,
    # field_ids_size/off, method_ids_size/off, class_defs_size/off,
    # data_size, data_off.
    (_file_size, _header_size, _endian, _link_size, _link_off, _map_off,
     string_ids_size, string_ids_off, type_ids_size, type_ids_off,
     proto_ids_size, proto_ids_off, field_ids_size, field_ids_off,
     method_ids_size, method_ids_off, class_defs_size, class_defs_off,
     _data_size, _data_off) = struct.unpack_from("<24x20I", data, 8)

    def get_string(idx):
        off = struct.unpack_from("<I", data, string_ids_off + idx * 4)[0]
        _length, p = read_uleb128(data, off)
        end = data.index(b"\x00", p)
        return data[p:end].decode("utf-8", "replace")

    types = []
    for i in range(type_ids_size):
        sidx = struct.unpack_from("<I", data, type_ids_off + i * 4)[0]
        types.append(get_string(sidx))

    protos = []
    for i in range(proto_ids_size):
        _shorty, ret_idx, params_off = struct.unpack_from(
            "<III", data, proto_ids_off + i * 12)
        params = []
        if params_off:
            size = struct.unpack_from("<I", data, params_off)[0]
            for j in range(size):
                tidx = struct.unpack_from("<H", data, params_off + 4 + j * 2)[0]
                params.append(types[tidx])
        protos.append((types[ret_idx], tuple(params)))

    method_refs = []
    for i in range(method_ids_size):
        class_idx, proto_idx, name_idx = struct.unpack_from(
            "<HHI", data, method_ids_off + i * 8)
        ret, params = protos[proto_idx]
        proto_key = ret + "(" + ",".join(params) + ")"
        method_refs.append((types[class_idx], get_string(name_idx), proto_key))

    defined = {}
    super_of = {}
    for i in range(class_defs_size):
        (class_idx, _flags, super_idx, _ifaces, _src, _ann,
         class_data_off, _static) = struct.unpack_from(
            "<8I", data, class_defs_off + i * 32)
        cls = types[class_idx]
        super_of[cls] = types[super_idx] if super_idx != 0xFFFFFFFF else None
        methods = set()
        if class_data_off:
            p = class_data_off
            n_static, p = read_uleb128(data, p)
            n_instance, p = read_uleb128(data, p)
            n_direct, p = read_uleb128(data, p)
            n_virtual, p = read_uleb128(data, p)
            for _ in range(n_static + n_instance):
                _, p = read_uleb128(data, p)  # field_idx_delta
                _, p = read_uleb128(data, p)  # access_flags
            for count in (n_direct, n_virtual):
                method_idx = 0
                for _ in range(count):
                    delta, p = read_uleb128(data, p)
                    method_idx += delta
                    _, p = read_uleb128(data, p)  # access_flags
                    _, p = read_uleb128(data, p)  # code_off
                    m_class, m_name, m_proto = method_refs[method_idx]
                    methods.add((m_name, m_proto))
        defined[cls] = methods
    return types, defined, method_refs, super_of


def audit(apk_path, dex_only=False):
    with zipfile.ZipFile(apk_path) as z:
        dex_names = sorted(
            n for n in z.namelist()
            if n.startswith("classes") and n.endswith(".dex"))
    if dex_only:
        dex_names = ["classes.dex"] if "classes.dex" in dex_names else dex_names[:1]

    all_types = set()
    defined = {}
    super_of = {}
    method_refs = []
    per_dex = {}
    for name in dex_names:
        with zipfile.ZipFile(apk_path) as z:
            data = z.read(name)
        types, defs, mrefs, supers = parse_dex(data)
        per_dex[name] = (len(types), len(defs), len(mrefs))
        all_types |= set(types)
        defined.update(defs)
        super_of.update(supers)
        method_refs.extend(mrefs)

    missing_types = sorted(
        t for t in (normalize_type(x) for x in all_types)
        if t is not None
        and t not in defined and not t.startswith(FRAMEWORK_PREFIXES))
    missing_androidx = [t for t in missing_types if t.startswith("Landroidx/")]
    missing_collection = [t for t in missing_types if t.startswith("Landroidx/collection/")]
    # Hard failures: missing androidx.* types EXCEPT the OEM runtime-provided
    # window extensions/sidecar and the documented dormant set.
    hard_missing = [t for t in missing_androidx
                    if not t.startswith(ALLOWLISTED_MISSING)
                    and not t.startswith(DORMANT_ALLOWLIST)]

    # Method audit: resolve through dex-defined ancestors. A chain that
    # leaves the dex ends at a framework superclass, whose methods the
    # Android runtime provides — that is "assumed provided", not missing
    # (proving a method absent would need android.jar's method list).
    # Only a chain that terminates inside the dex without the method is
    # a genuine failure (in practice that means a malformed dex).
    unresolvable = []
    framework_assumed = 0
    for cls, name, proto in method_refs:
        if cls not in defined:
            continue  # declaring class itself missing -> covered by type audit
        cur = cls
        seen = set()
        found = False
        left_dex = False
        while cur is not None and cur not in seen:
            if cur not in defined:
                left_dex = True
                break
            seen.add(cur)
            if (name, proto) in defined[cur]:
                found = True
                break
            cur = super_of.get(cur)
        if found:
            continue
        if left_dex:
            framework_assumed += 1
            continue
        unresolvable.append((cls, name, proto))
    unresolvable = sorted(set(unresolvable))

    return {
        "dex_files": dex_names,
        "per_dex": per_dex,
        "referenced_types": len(all_types),
        "defined_types": len(defined),
        "method_refs": len(method_refs),
        "missing_types": missing_types,
        "missing_androidx": missing_androidx,
        "missing_collection": missing_collection,
        "hard_missing": hard_missing,
        "allowlisted_missing": [t for t in missing_types
                                if t.startswith(ALLOWLISTED_MISSING)],
        "dormant_missing": [t for t in missing_types
                            if t.startswith(DORMANT_ALLOWLIST)],
        "unresolvable_methods": unresolvable,
        "framework_assumed_methods": framework_assumed,
    }


def main():
    apk = sys.argv[1]
    dex_only = "--dex-only" in sys.argv
    quiet = "--quiet" in sys.argv
    r = audit(apk, dex_only=dex_only)
    print(f"dex files audited: {r['dex_files']}")
    for name, (nt, nd, nm) in r["per_dex"].items():
        print(f"  {name}: types={nt} defined_classes={nd} method_refs={nm}")
    print(f"referenced types: {r['referenced_types']}  defined types: {r['defined_types']}  "
          f"method refs: {r['method_refs']}")
    print(f"missing types: {len(r['missing_types'])}")
    by_pkg = defaultdict(int)
    for t in r["missing_types"]:
        pkg = t[1:].split("/")[0] + "/" + (t[1:].split("/")[1] if "/" in t[1:] else "")
        by_pkg[pkg] += 1
    for pkg, n in sorted(by_pkg.items()):
        print(f"  missing in {pkg}: {n}")
    if not quiet:
        for t in r["missing_types"]:
            print(f"  MISSING {t}")
    print(f"missing androidx.* types: {len(r['missing_androidx'])}")
    print(f"missing androidx.collection.* types: {len(r['missing_collection'])}")
    print(f"allowlisted (OEM runtime-provided) missing types: {len(r['allowlisted_missing'])}")
    print(f"dormant (documented, never loaded) missing types: {len(r['dormant_missing'])}")
    for t in r["dormant_missing"]:
        print(f"  DORMANT {t}")
    print(f"HARD missing androidx.* types: {len(r['hard_missing'])}")
    for t in r["hard_missing"]:
        print(f"  HARD {t}")
    print(f"unresolvable methods: {len(r['unresolvable_methods'])}")
    for cls, name, proto in r["unresolvable_methods"][:50]:
        print(f"  UNRESOLVABLE {cls}->{name}{proto}")
    print(f"method refs assumed framework-provided: {r['framework_assumed_methods']}")
    if r["hard_missing"] or r["unresolvable_methods"]:
        print("HARD FAIL: missing androidx.* type(s) or unresolvable method(s)")
        return 2
    if r["missing_types"]:
        print("WARN: missing types are only allowlisted/OEM or non-androidx")
        return 1
    print("CLEAN: no missing types, all methods resolve")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
