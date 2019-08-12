# XML Diff/Merge Utilities

## Overview

The utilities work on any XML files; they are not XML-schema aware.

### XML Patch Operations
These utilities make use of a difference format conformant to standard "An Extensible Markup Language (XML) Patch Operations Framework Utilizing XML Path Language (XPath) Selectors", [IETF RFC 5261](https://tools.ietf.org/html/rfc5261). Another benefit of these utilities, aside from editing Orchestra files, is that they can be used for HTTP PATCH operations with XML payloads.

## Difference

The XmlDiff utility compares two XML files and generates a third file that represents their differences in RFC 5261 format. Differences are encoded as additions, replacements, or removals of XML elements and attributes.

To run the difference utility, run this command line:

```
java io.fixprotocol.xml.XmlDiff <in-file1> <in-file2> [output-file]
```
If the output file is not provided, then results go to the console.

## Merge

The XmlMerge utility takes a base XML file and a difference file and merges the two to produce an new XML file.

To run the merge utility, run this command line:

```
java io.fixprotocol.xml.XmlMerge <base-xml-file> <diff-file> <output-xml-file>
```