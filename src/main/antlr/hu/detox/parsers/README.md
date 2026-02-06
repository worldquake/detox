# ANTLR4
The [ANTLR4](https://github.com/antlr/antlr4/blob/master/doc/index.md) is used to implement some otherwise complex tasks easily.

These are compiled transparently as you modify them in Eclipse, and sometimes you need to use the `Source - Cleanup..` to remove errors which eclipse can clean-up automatically.

## The AmountCalculator
Is used to calculate Amounts with simple mathematical expressions.
Assignment with '=' supported, newline or ';' is the separator of commands.
Operators '-'|'+'|'/'|'*' can be used and grouped by '(' and ')'. Whitespaces ' ' and '\t' will be ignored.

Example:
```
VALUE=1km/2s
```

## The Shell

Is the language of the [Main](/src/main/resources/hu/detox/Main.md).

It is a very simple way to strip commands ending with ';' into multiple strings. Escapes like \" and \\ are supported to construct strings.

Example:
```
"package.Classname"
	Arg1 "Arg \"2\""
;
```
