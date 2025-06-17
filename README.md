# SemanticFileNaming

The Academic Tool For Defining Patterns And Standardizing Filenames




# Running the tool

The command: `java -Xmx8192M -jar SemanticFileNaming-1.0.jar Arg1 Arg2 Arg3`

* Arg 1: File path to a specification.
* Arg 2: File path to a list of file paths.
* Arg 3 [optional]: File path to a model.
* Arg 4 [optional]: Verbosity (integer).



If you are unable to compile, the jar file is also available under this repository's releases.




# Example

`data/spec.txt` is a list of specifications as well as additional models. Sections start with BEGIN ### where ### corresponds to the type of section. For example, Spec is the section containing the list of specifications.

`data/dir.txt` is a list of file paths, one per line. The name of the file "x" is used to generate the name of the output KR in ttl format at "x.ttl" in the current directory.

run `java -Xmx8192M -jar target/SemanticFileNaming-1.0.jar data/spec.txt data/dir.txt` to generate a `dir.txt.ttl` file in the current directory for the example data. The example data is the case study from the paper.
