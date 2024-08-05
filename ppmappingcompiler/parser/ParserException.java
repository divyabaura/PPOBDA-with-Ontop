package ppmappingcompiler.parser;

@SuppressWarnings("unused")
public class ParserException extends Exception {

    ParserException() {
        super();
    }

    ParserException(String formula) {
        this("formula", formula);
    }

    ParserException(String formulaType, String formula) {
        this(formulaType, formula, null);
    }

    ParserException(String formulaType, String formula, String reason) {
        super(String.format("Cannot parse %s:\n\t%s\n%s",
                formulaType, formula,
                reason != null ? String.format(" (%s)", reason) : ""
        ));
    }

}