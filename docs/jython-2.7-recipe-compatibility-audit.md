# Jython 2.5 to 2.7 Recipe Compatibility Audit

**Date:** 2026-01-30
**Scope:** artpro-nodel-recipes repository
**Recipes Scanned:** 100+ script.py files

## Executive Summary

This audit identifies breaking changes in Nodel recipes when migrating from Jython 2.5.4 to Jython 2.7.x. The most critical issue is **exception syntax** (`except Exception, e:`) which causes immediate `SyntaxError` in 15 recipes. Secondary concerns include dictionary access patterns that may raise `KeyError` at runtime.

## Risk Categories

| Risk Level | Description | Action Required |
|------------|-------------|-----------------|
| **HIGH** | Syntax errors preventing script load | Must fix before migration |
| **MEDIUM** | Runtime errors under certain conditions | Should fix, test thoroughly |
| **LOW** | Deprecated patterns, minor issues | Fix opportunistically |

---

## HIGH RISK: Exception Syntax (15 Recipes)

### Issue

Jython 2.5 accepted the Python 2.5 exception syntax:
```python
except Exception, e:  # SyntaxError in Jython 2.7
```

Jython 2.7 requires Python 2.6+ syntax:
```python
except Exception as e:  # Correct
```

### Affected Recipes

| Recipe | File Path | Occurrences |
|--------|-----------|-------------|
| PJLink | `PJLink/script.py` | 8 (lines 58, 69, 85, 98, 112, 124, 136, 164) |
| Projectiondesign Projector | `projectiondesign projector/script.py` | 2 (lines 64, 67) |
| Nodel Surveyor | `Nodel Surveyor/script.py` | 2 (lines 240, 243) |
| OSC Client | `OSC Client/script.py` | 1 (line 186) |
| Microsoft Exchange | `Microsoft Exchange schedule retriever/script.py` | 1 (line 103) |
| Network Monitors | `Network Monitors/script.py` | 1 (line 163) |
| Extron G2 Controllers | `Extron G2 series controllers/script.py` | 1 (line 262) |
| Dataprobe iBoot (current) | `Dataprobe iBoot/current/script.py` | 2 (lines 48, 75) |
| Dataprobe iBoot (legacy) | `Dataprobe iBoot/legacy/script.py` | 1 (line 45) |
| Advantech ADAM 6050/6060 | `Advantech ADAM 6050 6060 relay module/legacy/script.py` | 3 (lines 53, 64, 73) |
| TCP (retired) | `(retired)/tcp/script.py` | 2 (lines 19, 22) |
| Gen2 Show Control (retired) | `(retired)/gen2showcontrol/script.py` | 1 (line 42) |

### Fix

Global find-and-replace:
```bash
# From recipes root directory
find . -name "script.py" -exec sed -i '' 's/except \([A-Za-z]*\), \([a-z]*\):/except \1 as \2:/g' {} \;
```

Or manually replace each occurrence:
```
except Exception, e:  →  except Exception as e:
except KeyError, e:   →  except KeyError as e:
```

---

## MEDIUM RISK: Dictionary Access Patterns (8+ Recipes)

### Issue

In Jython 2.5, Java `Map` objects accessed via `map['key']` may return `None` for missing keys. In Jython 2.7, this raises `KeyError`, matching standard Python dict behavior.

Recipes that parse JSON responses or access Java Maps with direct bracket notation may fail if expected keys are missing.

### Affected Recipes

| Recipe | Risky Patterns | Notes |
|--------|----------------|-------|
| **Ubiquiti switches via UDM Pro API** | `switch_data['id']`, `switch_data['port_overrides']` | API response parsing; may fail if structure changes |
| **QSC Q-SYS Core** | `arg['control']`, `arg['value']` | Mixed usage - some `.get()`, some direct access |
| **Microsoft Exchange schedule retriever** | `raw['calendar']`, `raw['subject']` | Calendar event parsing |

### Recipes with Good Patterns (No Action Needed)

These recipes already use safe `.get()` access:

- `VLC media player/script.py` - Consistent `.get()` usage
- Most HTTP/REST recipes with modern code

### Fix

Replace direct access with `.get()` for optional keys:

```python
# Before (risky)
value = response['key']

# After (safe)
value = response.get('key')  # Returns None if missing
value = response.get('key', 'default')  # Returns default if missing

# For required keys, add explicit error handling
value = response.get('key')
if value is None:
    console.error('Missing required key')
    return
```

---

## LOW RISK: Print Statements (20+ Recipes)

### Issue

Python 2 print statements without parentheses:
```python
print 'message'  # Fails in Jython 2.7
```

Should use function syntax:
```python
print('message')  # Works in both
```

### Affected Recipes

Print statements are widespread but typically in debug/logging code paths. Notable examples:

- `projectiondesign projector/script.py` - 14 occurrences
- `Network Monitors/script.py` - Debug prints
- Various database and controller scripts

### Fix

Replace print statements with function calls:
```bash
# Automated fix (basic cases)
find . -name "script.py" -exec sed -i '' "s/print '\(.*\)'/print('\1')/g" {} \;
```

**Note:** Complex print statements with multiple arguments or formatting may need manual review.

---

## Patterns NOT Found (No Action Required)

| Pattern | Status | Notes |
|---------|--------|-------|
| `has_key()` | Not found | Good - this deprecated method is not used |
| `raw_input()` | Not found | Not applicable to Nodel scripts |
| Integer division issues | Not found | Standard division appears safe |
| `.keys()`, `.values()`, `.items()` iteration issues | Minimal risk | View vs list difference rarely impacts Nodel patterns |

---

## Migration Checklist

### Pre-Migration

- [ ] Run automated syntax fixes for exception handling
- [ ] Review and fix print statements in critical recipes
- [ ] Audit dictionary access in API-consuming recipes

### Testing

- [ ] Start Nodel with Jython 2.7
- [ ] Verify all HIGH RISK recipes load without SyntaxError
- [ ] Test MEDIUM RISK recipes with realistic API responses
- [ ] Monitor logs for `KeyError` exceptions during normal operation

### Post-Migration

- [ ] Update recipe authoring guidance to recommend `.get()` for optional keys
- [ ] Add linting rules for deprecated patterns
- [ ] Document breaking changes in release notes

---

## Recommended Compatibility Strategy

Based on the plan options in `docs/plans/jython-2.7-compatibility-plan.md`:

**Recommendation: Option B (Keep 2.7 behavior + migration guidance)**

Rationale:
1. The number of affected recipes is manageable (15 HIGH, 8+ MEDIUM)
2. Most issues are syntax-level and easily fixed with automated tools
3. Aligning with standard Python dict behavior is more predictable long-term
4. Wrapping Java Maps (Option A/C) adds complexity and hides real bugs

### Migration Guidance for Recipe Authors

```python
# Preferred: Use .get() for optional keys
value = data.get('optional_key')

# Preferred: Use .get() with defaults
value = data.get('optional_key', 'default_value')

# Required keys: Validate explicitly
required_value = data.get('required_key')
if required_value is None:
    console.error('Configuration error: required_key is missing')
    return

# Exception handling: Use 'as' keyword
try:
    risky_operation()
except Exception as e:
    console.error('Operation failed: %s' % e)

# Print: Use function syntax
print('Debug message')
```

---

## Appendix: Automated Fix Script

Save as `fix-jython27-compat.sh` and run from recipes root:

```bash
#!/bin/bash
# Jython 2.5 to 2.7 compatibility fixes

set -e

echo "Fixing exception syntax..."
find . -name "script.py" -exec grep -l "except.*," {} \; | while read file; do
    echo "  Fixing: $file"
    sed -i '' 's/except \([A-Za-z]*\), \([a-z]*\):/except \1 as \2:/g' "$file"
done

echo "Fixing simple print statements..."
find . -name "script.py" -exec grep -l "print '" {} \; | while read file; do
    echo "  Fixing: $file"
    sed -i '' "s/print '\([^']*\)'/print('\1')/g" "$file"
done

echo "Done. Please review changes and test thoroughly."
```

---

## References

- [Jython 2.7 Release Notes](https://www.jython.org/jython-old-sites/archive/27/release-27.html)
- [Python 2.6 Exception Syntax Change](https://docs.python.org/3/whatsnew/2.6.html#pep-3110-exception-handling-changes)
- `docs/plans/jython-2.7-compatibility-plan.md` - Project migration plan
