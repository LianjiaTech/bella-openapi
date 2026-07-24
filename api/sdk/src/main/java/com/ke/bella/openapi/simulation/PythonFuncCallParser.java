package com.ke.bella.openapi.simulation;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

public class PythonFuncCallParser {
    private final Reader reader;
    private int currentChar;
    private final StringBuilder buffer = new StringBuilder();

    // 当前解析状态
    private enum ParseState {
        INIT, IN_BLOCK, IN_FUNC_CALL, IN_ARGUMENTS, IN_DICT
    }

    private final Deque<ParseState> stateStack = new ArrayDeque<>();

    // 存储解析结果
    private final List<Map<String, Object>> funcCalls = new ArrayList<>();
    private Map<String, Object> currentCall;
    private Map<String, Object> currentDict;
    private PythonFuncCallListener listner;

    public PythonFuncCallParser(Reader reader, PythonFuncCallListener listner) {
        this.reader = reader;
        this.listner = listner;
        stateStack.push(ParseState.INIT);
        try {
            currentChar = reader.read();
        } catch (IOException e) {
            currentChar = -1;
        }
    }

    public List<Map<String, Object>> parse() throws IOException {
        while (currentChar != -1) {
            switch (stateStack.peek()) {
            case INIT:
                parseInit();
                break;
            case IN_BLOCK:
                parseCodeBlock();
                break;
            case IN_FUNC_CALL:
                parseFuncCall();
                break;
            case IN_ARGUMENTS:
                parseArguments();
                break;
            case IN_DICT:
                parseDict();
                break;
            }
        }
        listner.onFinish();
        return funcCalls;
    }

    private void parseInit() throws IOException {
        skipWhitespace();
        if(tryParseBlockStart()) {
            stateStack.pop();
            stateStack.push(ParseState.IN_BLOCK);
        } else {
            throw new RuntimeException("Expected code block start");
        }
    }

    private void parseCodeBlock() throws IOException {
        skipWhitespace();
        if(tryParseBlockEnd()) {
            stateStack.pop();
            return;
        }

        if(tryParseFuncCallStart()) {
            stateStack.push(ParseState.IN_FUNC_CALL);
            currentCall = new LinkedHashMap<>();
            return;
        }

        advance();
    }

    private void parseFuncCall() throws IOException {
        if(tryParseDirectlyResponse()) {
            parseDirectlyResponseArgs();
            stateStack.pop();
            return;
        }

        listner.onFunctionName(buffer.toString(), funcCalls.size());
        parseNormalCall();
        funcCalls.add(currentCall);
        stateStack.pop();
    }

    private void parseDirectlyResponseArgs() throws IOException {
        expect('(');
        parseRespTypeArg();
        expect(',');
        parseContentArg();
        skipWhitespace();
        expect(')');
    }

    private void parseNormalCall() throws IOException {
        parseIdentifier();

        expect('(');
        listner.onFunctionCallEnter();
        if(currentChar != ')') {
            stateStack.push(ParseState.IN_ARGUMENTS);
            parseArguments();
        }
        skipWhitespace();
        expect(')');
        listner.onFunctionCallExit();
    }

    private void parseArguments() throws IOException {
        boolean isFirst = true;
        do {
            if(!isFirst) {
                listner.onNextArgumentEnter();
            }
            parseNamedArgument();
            isFirst = false;
        } while (tryConsume(','));

        stateStack.pop();
    }

    private void parseNamedArgument() throws IOException {
        String name = parseIdentifier();
        listner.onArgumentName(name);
        expect('=');
        Object value = parseValue();
        listner.onArgumentValue(value);
        currentCall.put(name, value);
    }

    private Object parseValue() throws IOException {
        skipWhitespace();

        if(currentChar == '\'' || currentChar == '"') {
            return parseString();
        }
        if(Character.isDigit(currentChar) || currentChar == '-') {
            return parseNumber();
        }
        if(currentChar == '[') {
            advance();
            return parseArray();
        }
        if(currentChar == '{') {
            advance();
            stateStack.push(ParseState.IN_DICT);
            Map<String, Object> newDict = new LinkedHashMap<>();
            currentDict = newDict;
            parseDict();
            return newDict;
        }
        if(Character.isLetter(currentChar)) {
            String word = parseIdentifier();
            switch (word) {
            case "True":
                return true;
            case "False":
                return false;
            case "None":
                return null;
            default:
                throw new RuntimeException("Unexpected value: " + word);
            }
        }
        throw new RuntimeException("Unexpected value character: " + (char) currentChar);
    }

    private void parseDict() throws IOException {
        do {
            Map<String, Object> ref = currentDict;
            skipWhitespace();
            if(currentChar == '}') {
                break;
            } else {
                String key = parseString();
                expect(':');
                Object value = parseValue();
                ref.put(key, value);
            }
        } while (tryConsume(','));

        expect('}');
        stateStack.pop();
    }

    // 辅助方法
    private void advance() throws IOException {
        currentChar = reader.read();
    }

    private void advanceUnBlock() throws IOException {
        if(!reader.ready()) {
            currentChar = 0;
        } else {
            advance();
        }
    }

    private void skipWhitespace() throws IOException {
        while (Character.isWhitespace(currentChar)) {
            advance();
        }
    }

    private boolean tryConsume(int expected) throws IOException {
        if(currentChar == expected) {
            advance();
            return true;
        }
        return false;
    }

    private void expect(int expected) throws IOException {
        if(currentChar != expected) {
            throw new RuntimeException("Expected '" + (char) expected + "' but found '" + (char) currentChar + "'");
        }
        advance();
    }

    private String parseIdentifier() throws IOException {
        skipWhitespace();
        buffer.setLength(0);
        while (Character.isLetterOrDigit(currentChar) || currentChar == '_') {
            buffer.append((char) currentChar);
            advance();
        }
        return buffer.toString();
    }

    private String parseString() throws IOException {
        int quote = currentChar;
        advance(); // 跳过起始引号

        buffer.setLength(0);
        while (currentChar != quote) {
            if(currentChar == '\\') {
                advance(); // 跳过反斜杠
                switch (currentChar) {
                case 'n':
                    buffer.append('\n');
                    break;
                case 't':
                    buffer.append('\t');
                    break;
                case 'r':
                    buffer.append('\r');
                    break;
                case 'b':
                    buffer.append('\b');
                    break;
                case 'f':
                    buffer.append('\f');
                    break;
                case '\'':
                case '"':
                case '\\':
                    buffer.append((char) currentChar);
                    break;
                default:
                    buffer.append((char) currentChar);
                }
                advance();
            } else {
                if(currentChar == -1) {
                    throw new RuntimeException("Unclosed string");
                }
                buffer.append((char) currentChar);
                advance();
            }
        }
        advance(); // 跳过结束引号
        return buffer.toString();
    }

    private String parseContentString() throws IOException {
        buffer.setLength(0);
        int quote = currentChar;
        advance(); // 跳过起始引号

        while (currentChar != quote) {
            if(currentChar == 0) {
                if(buffer.length() > 0) {
                    listner.onDirectlyResponseContent(buffer.toString());
                    buffer.setLength(0);
                }
                LockSupport.parkNanos(1L);
                advance();
            } else if(currentChar == '\\') {
                advance(); // 跳过反斜杠
                switch (currentChar) {
                case 'n':
                    buffer.append('\n');
                    break;
                case 't':
                    buffer.append('\t');
                    break;
                case 'r':
                    buffer.append('\r');
                    break;
                case 'b':
                    buffer.append('\b');
                    break;
                case 'f':
                    buffer.append('\f');
                    break;
                case '\'':
                case '"':
                case '\\':
                    buffer.append((char) currentChar);
                    break;
                default:
                    buffer.append((char) currentChar);
                }
                advanceUnBlock();
            } else {
                if(currentChar == -1) {
                    throw new RuntimeException("Unclosed string");
                }
                buffer.append((char) currentChar);
                advanceUnBlock();
            }
        }
        advance(); // 跳过结束引号
        if(buffer.length() > 1) {
            listner.onDirectlyResponseContent(buffer.toString());
        }
        return buffer.toString();
    }

    private Number parseNumber() throws IOException {
        buffer.setLength(0);
        if(currentChar == '-') {
            buffer.append('-');
            advance();
        }

        while (Character.isDigit(currentChar)) {
            buffer.append((char) currentChar);
            advance();
        }

        if(currentChar == '.') {
            buffer.append('.');
            advance();
            while (Character.isDigit(currentChar)) {
                buffer.append((char) currentChar);
                advance();
            }
            return Double.parseDouble(buffer.toString());
        }

        return Integer.parseInt(buffer.toString());
    }

    private ArrayList<Object> parseArray() throws IOException {
        ArrayList<Object> list = new ArrayList<>();
        do {
            skipWhitespace();
            if(currentChar != ']') {
                list.add(parseValue());
            } else {
                break;
            }
        } while (tryConsume(','));
        expect(']');
        return list;
    }

    private boolean tryParseBlockStart() throws IOException {
        if(currentChar == '`') {
            buffer.setLength(0);
            for (int i = 0; i < 3; i++) {
                if(currentChar != '`')
                    return false;
                buffer.append((char) currentChar);
                advance();
            }
            if(!"python".equals(parseIdentifier()))
                return false;
            return currentChar == '\n';
        }
        return false;
    }

    private boolean tryParseBlockEnd() throws IOException {
        if(currentChar == '`') {
            for (int i = 0; i < 3; i++) {
                if(currentChar != '`')
                    return false;
                advance();
            }
            return true;
        }
        return false;
    }

    private boolean tryParseFuncCallStart() throws IOException {
        return Character.isLetter(currentChar) || currentChar == '_';
    }

    private boolean tryParseDirectlyResponse() throws IOException {
        buffer.setLength(0);
        while (Character.isLetterOrDigit(currentChar) || currentChar == '_' || currentChar == '-') {
            buffer.append((char) currentChar);
            advance();
        }
        if(buffer.toString().equals("directly_response")) {
            currentCall.put("type", "directly_response");
            return true;
        }
        // 回退处理普通调用
        currentCall.put("function", buffer.toString());
        return false;
    }

    private void parseRespTypeArg() throws IOException {
        expectStringIdentifier("type");
        expect('=');
        listner.onDirectlyResponseType(parseString());
    }

    private void parseContentArg() throws IOException {
        expectStringIdentifier("content");
        expect('=');
        currentCall.put("content", parseContentString());
    }

    private void expectStringIdentifier(String expected) throws IOException {
        String actual = parseIdentifier();
        if(!expected.equals(actual)) {
            throw new RuntimeException("Expected " + expected + " but found " + actual);
        }
    }
}
