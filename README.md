# XML Diff/Merge Utilities

## Overview
The utilities find the difference between two XML files and support merging a baseline with a difference file.

The motivation is that ordinary line difference programs are not suitable for XML. In XML, order of attributes is always insignificant while an ordinary diff program would show changed order as a difference. In XML, element order is significant for a sequence but not for other uses. 
The utilities work on any XML files; they are not XML-schema aware. Element order is handled by a switch; it can either be considered significant or insignificant. 

### XML Patch Operations
These utilities make use of a difference format conformant to standard "An Extensible Markup Language (XML) Patch Operations Framework Utilizing XML Path Language (XPath) Selectors", [IETF RFC 5261](https://tools.ietf.org/html/rfc5261). Another benefit of these utilities, aside from editing Orchestra files, is that they can be used for HTTP PATCH operations with XML payloads.

The patch format has no way to show moves. If element order is considered, then a move will be displayed as an add and remove.

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

## License
© Copyright 2017-2019 FIX Protocol Limited

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.