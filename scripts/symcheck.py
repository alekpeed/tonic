#!/usr/bin/env python3
"""Resolve every symbol a Kotlin file uses, without compiling.

    scripts/symcheck.py $(git diff --name-only HEAD -- '*.kt')

Why this exists. The sandboxes this project is developed in have no Android SDK, so CI is not only
the test runner but the *compiler* - and it takes five to nine minutes. Six consecutive CI rounds on
the Phase 3 branch were spent on unresolved references: a symbol left dangling when a scripted edit
silently matched nothing, an import never added, a rename applied to four of five call sites. Each
cost a full round to learn something a compiler states in a second.

This is not a type checker and cannot become one. It indexes every type, top-level function and
top-level value in the repository by package, then checks that each capitalized identifier a file
uses is either imported, declared in that file, or declared in its own package. That is exactly the
class of failure that has actually happened here.

Known false positives, all harmless: prose inside backtick test names (`... M2 node ...`), enum
entries referenced unqualified, and generated types like BuildConfig. Read the hits; do not assume
they are all real. A clean run is not a promise that the code compiles - it is a promise that
nothing is obviously unresolvable, which is the cheap half of the check.
"""
import re, sys, pathlib, collections

STDLIB = set("""
Int Long Double Float Boolean String Char Byte Short Unit Any Nothing List Set Map MutableList
MutableSet MutableMap Array FloatArray IntArray LongArray DoubleArray Pair Triple Exception
Throwable Comparable Iterable Collection Sequence Result Regex Math System Thread Runnable Number
Class Object Override JvmInline Volatile Suppress Deprecated JvmStatic JvmField Synchronized
ArrayDeque Comparator Iterator Enum Function0 Function1 Lazy Random Boolean CharSequence
T V R E K U Companion Builder Factory Entry Key Value Type Kind State Mode Scope
ShortArray ByteArray CharArray BooleanArray ShortArray UByte UShort UInt ULong
IllegalArgumentException IllegalStateException SecurityException RuntimeException NullPointerException
IndexOutOfBoundsException UnsupportedOperationException NumberFormatException ClassCastException
NoSuchElementException ArithmeticException ConcurrentModificationException AssertionError Error
""".split())

ROOT = pathlib.Path("/home/user/tonic")
DECL = re.compile(r'^\s*(?:@\w+\s+)*(?:public |internal |private |abstract |sealed |open |data |value |enum |annotation |inner )*'
                  r'(?:class|interface|object|typealias)\s+(\w+)', re.M)
TOPFUN = re.compile(r'^(?:public |internal |private )?(?:inline |suspend )*fun\s+(?:<[^>]*>\s+)?(\w+)\s*\(', re.M)
TOPVAL = re.compile(r'^(?:public |internal |private )?(?:const )?(?:val|var)\s+(\w+)', re.M)

pkg_symbols = collections.defaultdict(set)
for kt in ROOT.rglob("*.kt"):
    if "/build/" in str(kt):
        continue
    t = kt.read_text(errors="ignore")
    m = re.search(r'^package\s+([\w.]+)', t, re.M)
    if not m:
        continue
    p = m.group(1)
    for pat in (DECL, TOPFUN, TOPVAL):
        for d in pat.finditer(t):
            pkg_symbols[p].add(d.group(1))
    # enum entries and nested types are reachable via their owner; index them too
    for d in re.finditer(r'^\s{4,}(?:@\w+\s+)*(?:public |internal |private |abstract |sealed |open |data |value |inner )*'
                         r'(?:class|interface|object)\s+(\w+)', t, re.M):
        pkg_symbols[p].add(d.group(1))

def local_names(text):
    names = set()
    for m in re.finditer(r'^import\s+[\w.]*?\.(\w+)(?:\s+as\s+(\w+))?\s*$', text, re.M):
        names.add(m.group(2) or m.group(1))
    for m in re.finditer(r'^import\s+([\w.]+)\.\*', text, re.M):
        names |= pkg_symbols.get(m.group(1), set())
    for pat in (DECL, TOPFUN, TOPVAL,
                r'\b(?:class|interface|object)\s+(\w+)',
                r'\b(?:val|var)\s+(\w+)',
                r'\bconst\s+val\s+(\w+)',
                r'^\s*(\w+),?\s*$'):
        it = pat.finditer(text) if hasattr(pat, "finditer") else re.finditer(pat, text, re.M)
        for m in it:
            names.add(m.group(1))
    return names

def used(text):
    b = re.sub(r'^import .*$', '', text, flags=re.M)
    b = re.sub(r'/\*.*?\*/', '', b, flags=re.S)
    b = re.sub(r'//.*$', '', b, flags=re.M)
    b = re.sub(r'"""(?:.|\n)*?"""', '""', b)
    b = re.sub(r'"(?:[^"\\]|\\.)*"', '""', b)
    return {m.group(1) for m in re.finditer(r'(?<![.\w@])([A-Z][A-Za-z0-9_]*)\b', b)}

bad = False
for f in sys.argv[1:]:
    path = pathlib.Path(f)
    text = path.read_text()
    own_pkg = re.search(r'^package\s+([\w.]+)', text, re.M).group(1)
    known = local_names(text) | pkg_symbols.get(own_pkg, set()) | STDLIB
    missing = sorted(n for n in used(text) if n not in known)
    if missing:
        bad = True
        print(f"UNRESOLVED  {f}")
        for n in missing:
            print(f"      {n}")
print("CLEAN — every symbol resolves" if not bad else "^^ resolve these")
