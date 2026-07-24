grammar PythonFuncCall;

@header {
package com.ke.bella.openapi.simulation;
}

// Parser Rules
document    : BLOCK_START codeBlock BLOCK_END;
codeBlock   : funcCall+ NEWLINE;
funcCall : directly_response | normal_call;
directly_response: DIRECTLY_RESPONSE LPAREN resp_type_arg COMMA content_arg RPAREN;
resp_type_arg: RESP_TYPE EQUALS STRING;
content_arg: CONTENT EQUALS STRING;
normal_call: IDENTIFIER LPAREN arguments? RPAREN;
arguments : namedArgument (COMMA namedArgument)* ;
namedArgument : IDENTIFIER EQUALS value;

value : STRING                                                    # StringValue
      | NUMBER                                                   # NumberValue
      | BOOLEAN                                                  # BooleanValue
      | '[' (value (COMMA value)*)? ']'                          # ArrayValue
      | NULL                                                     # NullValue
      | LBRACE (dictPair (COMMA dictPair)*)? RBRACE            # DictValue
      ;

dictPair : STRING COLON value;

// Lexer Rules
BLOCK_START : '```python' NEWLINE;
BLOCK_END   : '```';
DIRECTLY_RESPONSE: 'directly_response';
LPAREN : '(' ;
RPAREN : ')' ;
LBRACE : '{' ;
RBRACE : '}' ;
COMMA : ',' ;
EQUALS : '=' ;
COLON : ':' ;
STRING
    : '"' ('\\' ["] | ~["\\\r\n])* '"'
    | '\'' ('\\' ['] | ~['\\\r\n])* '\''
    ;
NUMBER : '-'? [0-9]+ ('.' [0-9]+)?;
BOOLEAN : 'True' | 'False';
NULL : 'None';
RESP_TYPE: 'type';
CONTENT: 'content';
IDENTIFIER : [a-zA-Z_][a-zA-Z0-9_]*;
NEWLINE : [\r\n]+;
WS : [ \t]+ -> skip;