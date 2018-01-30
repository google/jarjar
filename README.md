This is not an officially supported Google product.

*Embedding Java libraries since 2004*

**Jar Jar Links** is a utility that makes it easy to repackage Java libraries
and embed them into your own distribution. This is useful for two reasons:

*   You can easily ship a single jar file with no external dependencies.

*   You can avoid problems where your library depends on a specific version of a
    library, which may conflict with the dependencies of another library.

## How does it work?

Jar Jar Links includes an Ant task that extends the built-in jar task. The
normal `zipfileset` element is used to embed jar files. A new rule element is
added which uses wildcards patterns to rename the embedded class files. Bytecode
transformation (via ASM) is used to change references to the renamed classes,
and special handling is provided for moving resource files and transforming
string literals.
