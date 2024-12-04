package edu.university.ecs.lab.common.models.enums;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import edu.university.ecs.lab.common.models.ir.MethodCall;
import edu.university.ecs.lab.intermediate.utils.StringParserUtils;
import javassist.expr.Expr;
import lombok.Getter;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Enum to represent Spring methodName and HttpMethod combinations and determine HttpMethod from
 * methodName.
 */
@Getter
public class RestCallTemplate {
    public static final Set<String> REST_OBJECTS = Set.of("RestTemplate", "OAuth2RestOperations", "OAuth2RestTemplate", "WebClient");
    public static final Set<String> REST_METHODS = Set.of("getForObject", "postForObject", "patchForObject", "put", "delete", "exchange", "get", "post", "options", "patch");
    private static final String UNKNOWN_VALUE = "{?}";

    private final String url;
    private final HttpMethod httpMethod;
    private final CompilationUnit cu;
    private final MethodCallExpr mce;

    public RestCallTemplate(MethodCallExpr mce, MethodCall mc, CompilationUnit cu) {
        this.cu = cu;
        this.mce = mce;
        this.url = simplifyEndpointURL(preParseURL(mce, mc));
        this.httpMethod = getHttpFromName(mce);
    }

    /**
     * Find the RestTemplate by the method name.
     *
     * @param mce the method call
     * @return the RestTemplate found (null if not found)
     */
    public HttpMethod getHttpFromName(MethodCallExpr mce) {
        String methodName = mce.getNameAsString();
        switch (methodName) {
            case "getForObject":
            case "get":
                return HttpMethod.GET;
            case "postForObject":
            case "post":
                return HttpMethod.POST;
            case "patchForObject":
            case "patch":
                return HttpMethod.PATCH;
            case "put":
                return HttpMethod.PUT;
            case "delete":
                return HttpMethod.DELETE;
            case "exchange":
                return getHttpMethodForExchange(mce.getArguments().stream().map(Node::toString).collect(Collectors.joining()));
        }

        return HttpMethod.NONE;
    }

    /**
     * Get the HTTP method for the JSF exchange() method call.
     *
     * @param arguments the arguments of the exchange() method
     * @return the HTTP method extracted
     */
    public HttpMethod getHttpMethodForExchange(String arguments) {
        if (arguments.contains("HttpMethod.POST")) {
            return HttpMethod.POST;
        } else if (arguments.contains("HttpMethod.PUT")) {
            return HttpMethod.PUT;
        } else if (arguments.contains("HttpMethod.DELETE")) {
            return HttpMethod.DELETE;
        } else if (arguments.contains("HttpMethod.PATCH")) {
            return HttpMethod.PATCH;
        } else {
            return HttpMethod.GET; // default
        }
    }

    /**
     * Find the URL from the given expression.
     *
     * @param exp the expression to extract url from
     * @return the URL found
     */
    private String parseURL(Expression exp) {
        if (exp.isStringLiteralExpr()) {
            return exp.asStringLiteralExpr().asString();
        } else if (exp.isFieldAccessExpr()) {
            return parseFieldValue(exp.asFieldAccessExpr().getNameAsString());
        } else if (exp.isBinaryExpr()) {
            String left = parseURL(exp.asBinaryExpr().getLeft());
            String right = parseURL(exp.asBinaryExpr().getRight());
            return left + right;
        } else if(exp.isEnclosedExpr()) {
            return parseURL(exp.asEnclosedExpr());
        // Base case, if we are a method call or a u
        } else if(exp.isMethodCallExpr()) {
            // Here we may try to find a modified url in a method call expr
            return backupParseURL(exp).isEmpty() ? UNKNOWN_VALUE : backupParseURL(exp);
        } else if(exp.isNameExpr()) {
            // Special case
            if(exp.asNameExpr().getNameAsString().contains("uri") || exp.asNameExpr().getNameAsString().contains("url")) {
                return "";
            }
            return UNKNOWN_VALUE;
        }

        // If all fails, try to find some viable url
        return backupParseURL(exp);
    }

    /**
     * Find the URL from the given expression.
     *
     * @param exp the expression to extract url from
     * @return the URL found
     */
    private String backupParseURL(Expression exp) {
        // Regular expression to match the first instance of "/.*"
        String regex = "\".*(/.+?)\"";

        // Compile the pattern
        Pattern pattern = Pattern.compile(regex);

        // Create a matcher for the input string
        Matcher matcher = pattern.matcher(exp.toString());

        // Find the first match
        if (matcher.find()) {
            // Extract the first instance of "/.*"
            String extracted = matcher.group(0).replace("\"", "");  // Group 1 corresponds to the part in parentheses (captured group)

            // Replace string formatters if they are present
            extracted = extracted.replaceAll("%[sdif]", UNKNOWN_VALUE);

            return cleanURL(extracted);
        }

        return "";

    }

    /**
     * Shorten URLs to only endpoint query
     * 
     * @param str full url
     * @return url query
     */
    private static String cleanURL(String str) {
        str = str.replace("http://", "");
        str = str.replace("https://", "");

        // Remove everything before the first /
        int backslashNdx = str.indexOf("/");
        if (backslashNdx > 0) {
            str = str.substring(backslashNdx);
        }

        // Remove any trailing quotes
        if (str.endsWith("\"")) {
            str = str.substring(0, str.length() - 1);
        }

        // Remove trailing / (does not affect functionality, trailing /'s are ignored in Spring)
        if (str.endsWith("/")) {
            str = str.substring(0, str.length() - 1);
        }

        return str;
    }

    private String parseFieldValue(String fieldName) {
        for (FieldDeclaration fd : cu.findAll(FieldDeclaration.class)) {
            if (fd.getVariables().toString().contains(fieldName)) {
                Expression init = fd.getVariable(0).getInitializer().orElse(null);
                if (init != null) {
                    return StringParserUtils.removeOuterQuotations(init.toString());
                }
            }
        }

        return "";
    }


    private String preParseURL(MethodCallExpr mce, MethodCall mc) {

        // Nuance for webclient with method appender pattern
        if(mc.getObjectType().equals("WebClient")) {
            if(mce.getParentNode().isPresent()) {
                if(mce.getParentNode().get() instanceof MethodCallExpr pmce) {
                    return pmce.getArguments().get(0).toString().isEmpty() ? "" : cleanURL(parseURL(pmce.getArguments().get(0)));
                }
            }
        } else {
            return mce.getArguments().get(0).toString().isEmpty() ? "" : cleanURL(parseURL(mce.getArguments().get(0)));
        }

        return "";
    }

    /**
     * Simplifies all path arguments to {?}.
     *
     * @param url the endpoint URL
     * @return the simplified endpoint URL
     */
    public static String simplifyEndpointURL(String url) {
        return url.replaceAll("\\{[^{}]*\\}", "{?}");
    }
}
