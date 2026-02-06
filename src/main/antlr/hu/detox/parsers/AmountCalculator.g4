grammar AmountCalculator;

@header {
    import java.util.HashMap;
	import org.jscience.physics.amount.Amount;
	import java.util.Map;
	import javax.measure.unit.SI;
}

@parser::members {
	public HashMap memory;
	private static final java.util.regex.Pattern NUMERIC = java.util.regex.Pattern.compile("[0-9]+");
}

/*----------------
* PARSER RULES
*----------------*/
prog returns [Object value] :
	s1=stat { $value = $s1.value;}
	(SEPCMD s2=stat { $value = $s2.value;})*
;
stat returns [Object value] :
	{String key = ".";}
	(ID '=' { key = $ID.text; } )?
	expr {
		$value = $expr.value;
		memory.put(key, $value);
	}
;
expr returns [Object value] :
	e=multExpr {$value = $e.value;} (
		'+'? e=multExpr {$value = AmountCalculator.plus($value, $e.value);}
		| '-' e=multExpr {$value = AmountCalculator.minus($value, $e.value);}
	)*
;
multExpr returns [Object value] :
	e=atom {$value = $e.value;} (
		'*' e=atom {$value = AmountCalculator.times($value, $e.value);}
		| '/' e=atom {$value = AmountCalculator.divide($value, $e.value);}
	)*
;
atom returns [Object value] :
	ID {
		String txt = $ID.text;
		$value = memory == null ? null : memory.get(txt);
		if($value == null) if(NUMERIC.matcher(txt).matches()) {
			$value = Double.parseDouble(txt);
		} else {
			$value = Amount.valueOf(txt);
		}
	}
	| '(' expr ')' { $value = $expr.value; }
;

/*----------------
* LEXER RULES
*----------------*/
ID 	   : ~(' '|'\t'|'\r'|'\n'|'='|';'|'-'|'+'|'/'|'*'|'('|')')+;
SEPCMD : ('\r'? '\n'|';');
WS 	   : (' '|'\t')+  {skip();};
