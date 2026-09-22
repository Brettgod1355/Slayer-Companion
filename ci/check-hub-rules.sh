#!/usr/bin/env bash
# Greps src/main/java for APIs and language features the RuneLite Plugin Hub
# rejects. Comment lines are ignored so the rules can be documented in code.
# Exit 1 on any hit.
set -uo pipefail

src="src/main/java"
fail=0

strip_comments() {
	# drop // line comments and /* */ block comments (single-line and multi-line)
	sed -E 's://.*$::' "$1" | perl -0pe 's:/\*.*?\*/::gs'
}

check() {
	local label="$1" pattern="$2"
	local hits
	hits=$(for f in $(find "$src" -name '*.java'); do
		strip_comments "$f" | grep -nE "$pattern" | sed "s|^|$f:|"
	done)
	if [[ -n "$hits" ]]; then
		echo "::error::Forbidden: $label"
		echo "$hits"
		fail=1
	fi
}

check "java.net.HttpURLConnection / URL.openConnection (inject OkHttpClient instead)" 'HttpURLConnection|openConnection\('
check "java.awt.Desktop (use LinkBrowser)" 'java\.awt\.Desktop|Desktop\.getDesktop'
check "direct file I/O (use Filepath from getPluginDirectory)" 'java\.io\.File\b|java\.nio\.file|\bFiles\.|Paths\.get\(|\.toFile\(\)|FileChannel\.open|RuneLite\.[A-Z_]*DIR|new FileReader|new FileWriter|new FileInputStream|new FileOutputStream'
check "Filepath.Unchecked" 'Filepath\.Unchecked'
check "Thread.sleep" 'Thread\.sleep\('
check "Thread.interrupt / isInterrupted" '\.interrupt\(\)|isInterrupted\(\)'
check "reflection" 'java\.lang\.reflect|java\.lang\.invoke|Class\.forName|\.getMethod\(|\.getDeclaredMethod|\.getField\(|\.getDeclaredField|\.getAnnotation\(|setAccessible|Proxy\.newProxyInstance|MethodHandles|\.newInstance\('
check "JNI / external processes / runtime code loading" 'System\.loadLibrary|System\.load\(|Runtime\.getRuntime\(\)\.exec|ProcessBuilder|URLClassLoader|defineClass|javax\.script|jdk\.jshell'
check "networking" 'java\.net\.Socket|java\.net\.ServerSocket|OkHttpClient|okhttp3|HttpClient'

# Non-Java sources are not allowed in the plugin.
if find src/main -type f \( -name '*.kt' -o -name '*.scala' -o -name '*.groovy' \) | grep -q .; then
	echo "::error::Only Java sources are allowed under src/main"
	fail=1
fi

# Dependencies must be compileOnly or test-only.
if grep -nE '^\s*(implementation|api|runtimeOnly)\b' build.gradle; then
	echo "::error::build.gradle declares a bundled dependency; use compileOnly or testImplementation"
	fail=1
fi

if [[ $fail -eq 0 ]]; then
	echo "Hub rules check passed"
fi
exit $fail
