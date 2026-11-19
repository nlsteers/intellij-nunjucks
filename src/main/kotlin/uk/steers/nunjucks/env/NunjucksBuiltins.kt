package uk.steers.nunjucks.env

/** A built-in filter, test or global function. [signature] excludes the piped/tested value. */
data class Builtin(val name: String, val signature: String, val description: String, val aliasOf: String? = null) {
    /** Whether the first parameter is mandatory, so completion should insert parentheses. */
    val requiresArguments: Boolean
        get() {
            val first = signature.removePrefix("(").substringBefore(",").substringBefore(")").trim()
            return first.isNotEmpty() && '=' !in first && !first.endsWith("?") && !first.startsWith("...")
        }
}

object NunjucksBuiltins {
    val FILTERS: Map<String, Builtin> = listOf(
        Builtin("abs", "", "Return the absolute value of the argument."),
        Builtin("batch", "(linecount, fill_with?)", "Return a list of lists with the given number of items, padding the last with fill_with."),
        Builtin("capitalize", "", "Make the first letter uppercase and the rest lower case."),
        Builtin("center", "(width=80)", "Centre the value in a field of a given width."),
        Builtin("default", "(value, boolean=false)", "Return value if the input is undefined (or falsy when boolean is true), otherwise the input."),
        Builtin("d", "(value, boolean=false)", "Alias of default.", aliasOf = "default"),
        Builtin("dictsort", "(caseSensitive=false, by=\"key\")", "Sort a dict and yield (key, value) pairs."),
        Builtin("dump", "(spaces?)", "Call JSON.stringify on an object and dump the result into the template."),
        Builtin("escape", "", "Convert the characters &, <, >, ‘, and ” to HTML-safe sequences."),
        Builtin("e", "", "Alias of escape.", aliasOf = "escape"),
        Builtin("first", "", "Get the first item in an array or the first letter of a string."),
        Builtin("float", "(default=0.0)", "Convert a value into a floating point number."),
        Builtin("forceescape", "", "Enforce HTML escaping, even on values marked safe."),
        Builtin("groupby", "(attribute)", "Group a sequence of objects by a common attribute."),
        Builtin("indent", "(width=4, indentfirst=false)", "Indent a string using spaces."),
        Builtin("int", "(default=0)", "Convert the value into an integer."),
        Builtin("join", "(delimiter=\"\", attribute?)", "Return a string which is the concatenation of the strings in a sequence."),
        Builtin("last", "", "Get the last item in an array or the last letter of a string."),
        Builtin("length", "", "Return the length of an array, string, or the number of keys in an object."),
        Builtin("list", "", "Convert the value into a list."),
        Builtin("lower", "", "Convert string to all lower case."),
        Builtin("nl2br", "", "Replace new lines with <br /> HTML elements."),
        Builtin("random", "", "Select a random value from an array."),
        Builtin("reject", "(test?, ...args)", "Filter a sequence of objects by applying a test and rejecting those that pass."),
        Builtin("rejectattr", "(attribute)", "Filter a sequence of objects, rejecting those whose attribute is truthy."),
        Builtin("replace", "(old, new, maxCount?)", "Replace one item with another. The first item can be a regular expression."),
        Builtin("reverse", "", "Reverse a string or array."),
        Builtin("round", "(precision=0, method=\"common\")", "Round a number. method is \"common\", \"ceil\" or \"floor\"."),
        Builtin("safe", "", "Mark the value as safe, so it will not be escaped when autoescaping is on."),
        Builtin("select", "(test?, ...args)", "Filter a sequence of objects by applying a test and selecting those that pass."),
        Builtin("selectattr", "(attribute)", "Filter a sequence of objects, selecting those whose attribute is truthy."),
        Builtin("slice", "(slices, fill_with?)", "Slice an iterator and return a list of lists containing those items."),
        Builtin("sort", "(reverse=false, caseSens=false, attr?)", "Sort an array."),
        Builtin("string", "", "Convert an object to a string."),
        Builtin("striptags", "(preserve_linebreaks=false)", "Strip SGML/XML tags and collapse whitespace."),
        Builtin("sum", "(attribute?, start=0)", "Output the sum of items in the array."),
        Builtin("title", "", "Make the first letter of each word uppercase."),
        Builtin("trim", "", "Strip leading and trailing whitespace."),
        Builtin("truncate", "(length=255, killwords=false, end=\"...\")", "Return a truncated copy of the string."),
        Builtin("upper", "", "Convert the string to upper case."),
        Builtin("urlencode", "", "Escape strings for use in URLs, using UTF-8 encoding."),
        Builtin("urlize", "(length?, nofollow?)", "Convert URLs in plain text into clickable links."),
        Builtin("wordcount", "", "Count and output the number of words in a string."),
    ).associateBy { it.name }

    val TESTS: Map<String, Builtin> = listOf(
        Builtin("callable", "", "Whether the value is a function."),
        Builtin("defined", "", "Whether the value is not undefined."),
        Builtin("divisibleby", "(n)", "Whether the value is divisible by n."),
        Builtin("escaped", "", "Whether the value has been escaped (marked safe)."),
        Builtin("equalto", "(other)", "Whether the value is strictly equal (===) to other."),
        Builtin("eq", "(other)", "Alias of equalto.", aliasOf = "equalto"),
        Builtin("sameas", "(other)", "Alias of equalto.", aliasOf = "equalto"),
        Builtin("even", "", "Whether the value is an even number."),
        Builtin("falsy", "", "Whether the value is falsy."),
        Builtin("ge", "(other)", "Whether the value is greater than or equal to other."),
        Builtin("greaterthan", "(other)", "Whether the value is greater than other."),
        Builtin("gt", "(other)", "Alias of greaterthan.", aliasOf = "greaterthan"),
        Builtin("iterable", "", "Whether the value is iterable."),
        Builtin("le", "(other)", "Whether the value is less than or equal to other."),
        Builtin("lessthan", "(other)", "Whether the value is less than other."),
        Builtin("lt", "(other)", "Alias of lessthan.", aliasOf = "lessthan"),
        Builtin("lower", "", "Whether the string is lowercase."),
        Builtin("mapping", "", "Whether the value is a plain object (dictionary)."),
        Builtin("ne", "(other)", "Whether the value is not strictly equal (!==) to other."),
        Builtin("null", "", "Whether the value is null."),
        Builtin("number", "", "Whether the value is a number."),
        Builtin("odd", "", "Whether the value is an odd number."),
        Builtin("string", "", "Whether the value is a string."),
        Builtin("truthy", "", "Whether the value is truthy."),
        Builtin("undefined", "", "Whether the value is undefined."),
        Builtin("upper", "", "Whether the string is uppercase."),
    ).associateBy { it.name }

    val GLOBALS: Map<String, Builtin> = listOf(
        Builtin("range", "(start, stop, step=1)", "Iterate over a fixed range of numbers. If only one argument is given it is the stop value."),
        Builtin("cycler", "(...items)", "Cycle through items; call next() to get the next one."),
        Builtin("joiner", "(separator=\",\")", "Return the separator on every call except the first."),
    ).associateBy { it.name }

    val LOOP_PROPERTIES: Map<String, String> = linkedMapOf(
        "index" to "The current iteration of the loop (1 indexed)",
        "index0" to "The current iteration of the loop (0 indexed)",
        "revindex" to "Number of iterations until the end (1 indexed)",
        "revindex0" to "Number of iterations until the end (0 indexed)",
        "first" to "Whether this is the first iteration",
        "last" to "Whether this is the last iteration",
        "length" to "Total number of items",
    )
}
