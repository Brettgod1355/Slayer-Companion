#!/usr/bin/env bash
# Greps Java sources for APIs and language features the RuneLite Plugin Hub rejects.
# String literals and comments are blanked first so documentation and URLs do not trip it,
# and so a forbidden call cannot hide behind a "//" inside a string.
# Usage: ci/check-hub-rules.sh [source dir]   (default src/main/java). Exit 1 on any hit.
set -uo pipefail

src="${1:-src/main/java}"
fail=0

strip_literals_and_comments() {
	perl -0pe 's:/\*.*?\*/::gs' "$1" | perl -pe 's{"(?:\\.|[^"\\])*"}{""}g; s{//.*$}{}'
}

check() {
	local label="$1" pattern="$2"
	local hits
	hits=$(for f in $(find "$src" -name '*.java'); do
		strip_literals_and_comments "$f" | grep -nE "$pattern" | sed "s|^|$f:|"
	done)
	if [[ -n "$hits" ]]; then
		echo "::error::Forbidden: $label"
		echo "$hits"
		fail=1
	fi
}

check "java.net.HttpURLConnection / URL.openConnection / openStream (inject OkHttpClient instead)" \
	'HttpURLConnection|openConnection\(|\.openStream\(|java\.net\.(URL|URI|Socket|ServerSocket|DatagramSocket|http)|javax\.net'
check "java.awt.Desktop (use LinkBrowser)" 'java\.awt\.Desktop|Desktop\.getDesktop'
check "direct file I/O (use Filepath from getPluginDirectory)" \
	'java\.io\.\*|java\.io\.File\b|java\.nio\.file|\bFiles\.|Paths\.get\(|Path\.of\(|FileSystems\.|\.toFile\(\)|FileChannel\.open|RuneLite\.[A-Z_]*DIR|\bnew (File|RandomAccessFile|PrintWriter|PrintStream|FileReader|FileWriter|FileInputStream|FileOutputStream|Scanner)\('
check "Filepath.Unchecked" 'Filepath\.Unchecked'
check "Thread.sleep" 'Thread\.sleep\('
check "Thread.interrupt / isInterrupted" '\.interrupt\(\)|isInterrupted\(\)'
check "reflection" \
	'java\.lang\.reflect|java\.lang\.invoke|Class\.forName|\.getMethods?\(|\.getDeclaredMethods?\(|\.getFields?\(|\.getDeclaredFields?\(|\.getConstructors?\(|\.getDeclaredConstructors?\(|\.getAnnotations?\(|setAccessible|Proxy\.newProxyInstance|MethodHandles|\.newInstance\('
check "JNI / external processes / runtime code loading" \
	'System\.loadLibrary|System\.load\(|Runtime\.getRuntime\(\)|ProcessBuilder|URLClassLoader|defineClass|javax\.script|jdk\.jshell|ClassLoader'
check "networking clients" 'OkHttpClient|okhttp3|HttpClient'

# Non-Java sources are not allowed in the plugin.
if find "$(dirname "$src")" -type f \( -name '*.kt' -o -name '*.scala' -o -name '*.groovy' \) 2>/dev/null | grep -q .; then
	echo "::error::Only Java sources are allowed under $(dirname "$src")"
	fail=1
fi

# Dependencies must be compileOnly or test-only.
if [[ -f build.gradle ]] && grep -nE '^\s*(implementation|api|runtimeOnly)\b' build.gradle; then
	echo "::error::build.gradle declares a bundled dependency; use compileOnly or testImplementation"
	fail=1
fi

if [[ $fail -eq 0 ]]; then
	echo "Hub rules check passed for $src"
fi
exit $fail
