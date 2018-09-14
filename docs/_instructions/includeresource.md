---
layout: default
class: Builder
title: -includeresource iclause   
summary:  Include resources from the file system
---

The resources will be copied into the target jar file. The `iclause` can have the following forms:

```
  iclause    ::= inline | copy
  copy       ::= '{' process '}' | process
  process    ::= assignment | simple
  assignment ::= PATH '=' simple
  simple     ::= PATH parameter*
  inline     ::= '@' PATH ( '!/' PATH? ('/**' | '/*')? )?
  parameters ::= 'flatten' | 'recursive' | 'filter'
```

In the case of `assignment` or `simple`, the PATH parameter can point to a file or directory. It is also possible to use the name.ext path of a JAR file on the classpath, that is, ignoring the directory. The `simple` form will place the resource in the target JAR with only the file name, therefore without any path components. That is, including src/a/b.c will result in a resource b.c in the root of the target JAR. 

If the PATH points to a directory, the directory name itself is not used in the target JAR path. If the resource must be placed in a subdirectory of the target jar, use the `assignment` form. If the file is not found, bnd will traverse the classpath to see of any entry on the classpath matches the given file name (without the directory) and use that when it matches. The `inline` requires a ZIP or JAR file, which will be completely expanded in the target JAR (except the manifest), unless followed with a file specification. The file specification can be a specific file in the jar or a directory followed by ** or *. The ** indicates recursively and the * indicates one level. If just a directory name is given, it will mean **.

The `simple` and `assigment` forms can be encoded with curly braces, like `{foo.txt}`. This indicates that the file should be preprocessed (or filtered as it is sometimes called). Preprocessed files can use the same variables and macros as defined in the macro section.

The `recursive:` directive indicates that directories must be recursively included.

The `flatten:` directive indicates that if the directories are recursively searched, the output must not create any directories. That is all resources are flattened in the output directory.

The `filter:` directive is an optional filter on the resources. This uses the same format as the instructions. Only the file name is verified against this instruction.

 Include-Resource: @osgi.jar,[=\ =]
    {LICENSE.txt},[=\ =]
    acme/Merge.class=src/acme/Merge.class

#### Sample usages:

##### Simple form:

| Instruction | Explanation |
| --- | --- |
| `-includeresource: lib/fancylibrary-3.12.0.jar` | Copy lib/fancylibrary-3.12.0.jar file into the root of the target JAR |
| `-includeresource.resources: -src/main/resources` | Copy folder src/main/resources contents (including subdfolders) into root of the target JAR <br>The arbitrarily named suffix .resources prevents this includeresource directive to be overwritten <br>The preceding minus sign instructs to supress an error for non-existing folder src/main/resources |
| `-includeresource: ${workspace}/LICENSE, {readme.md}` | Copy the LICENSE file residing in the bnd workspace folder (above the project directory) as well as the pre-processed readme.md file (allowing for e.g. variable substitution) in the project folder into the target JAR |
| `-includeresource: ${repo;com.acme:foo;latest}` | Copy the com.acme.foo bundle JAR in highest version number found in the bnd workspace repository into the root of the target JAR |

##### Assignment form:

| Instruction | Explanation |
| --- | --- |
| `-includeresource: images/=img/` or <br>`-includeresource: images=img` | Copy contents of img/ folder (including subdfolders) into an images folder of the target JAR |
| `-includeresource: x=a/c/c.txt` | Copy a/c/c.txt into file x in the root folder of the target JAR |
| `-includeresource: x/=a/c/c.txt` | Copy a/c/c.txt into file x/c.txt in the root folder of the target JAR |
| `-includeresource: libraries/fancylibrary.jar=lib/fancylibrary-3.12.jar; lib:=true` | Copy lib/fancylibrary-3.1.2.jar from project into libraries folder of the target JAR, and place it on the Bundle-Classpath (BCP). It will make sure the BCP starts with '.' and then each include resource that is included will be added to the BCP |
| `-includeresource: lib/; lib:=true` | Copy every JAR file underneath lib in a relative position under the root folder of the target JAR, and add each library to the bundle classpath |
| `-includeresource: acme-foo-snap.jar=${repo;com.acme:foo;snapshot}` | Copy the highest snapshot version of com.acme.foo found in the bnd workspace repository as acme-foo-snap.jar into the root of the target JAR |
| `-includeresource: foo.txt;literal='foo bar'` | Create a file named foo.txt containing the string literal "foo bar" in the root folder of the target JAR |
| `-includeresource: bsn.txt;literal='${bsn}'` | Create a file named bsn.txt containing the bundle symbolic name (bsn) of this project in the root folder of the target JAR |
| `-includeresource: libraries/=lib/;filter:=fancylibrary-*.jar;recursive:=false;lib:=true` or <br>`-includeresource: libraries/=lib/fancylibrary-*.jar;lib:=true` (as of bndtools 4.2) | Copy a wildcarded library from lib/ into libraries and add it to the bundle classpath |

##### Inline form:

| Instruction | Explanation |
| --- | --- |
| `-includeresource: @lib/fancylibrary-3.12.jar!/**` | Extract the contents of lib/fancylibrary-3.12.jar into the root folder of the target JAR, preserving relative paths |
| `-includeresource: @${repo;com.acme.foo;latest}!/!META-INF/*` | Extract the contents of the highest found com.acme.foo version in the bnd workspace repository into the root folder of the target JAR, preserving relative paths, excluding the META-INF/ folder |
