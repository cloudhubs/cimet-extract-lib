package edu.university.ecs.lab.common.models.enums;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import edu.university.ecs.lab.intermediate.utils.StringParserUtils;
import lombok.Getter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Factory class for generating an endpoint template from annotations
 */
@Getter
public class EndpointTemplate {
    public static final List<String> ENDPOINT_ANNOTATIONS = Arrays.asList("RequestMapping", "GetMapping", "PutMapping", "PostMapping", "DeleteMapping", "PatchMapping");
    private final HttpMethod httpMethod;
    private final String name;
    private final String url;



    public EndpointTemplate(AnnotationExpr requestMapping, AnnotationExpr endpointMapping) {
        HttpMethod finalHttpMethod = HttpMethod.ALL;

        String preUrl = "";
        if(requestMapping != null) {
            if (requestMapping instanceof NormalAnnotationExpr) {
                NormalAnnotationExpr nae = (NormalAnnotationExpr) requestMapping;
                for (MemberValuePair pair : nae.getPairs()) {
                    if (pair.getNameAsString().equals("value")) {
                        preUrl = pair.getValue().toString().replaceAll("\"", "");
                    }
                }
            } else if (requestMapping instanceof SingleMemberAnnotationExpr) {
                preUrl = requestMapping.asSingleMemberAnnotationExpr().getMemberValue().toString().replaceAll("\"", "");
            }
        }

        String url = "";
        if (endpointMapping instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr nae = (NormalAnnotationExpr) endpointMapping;
            for (MemberValuePair pair : nae.getPairs()) {
                if (pair.getNameAsString().equals("method")) {
                    String methodValue = pair.getValue().toString();
                    finalHttpMethod = httpFromMapping(methodValue);
                } else if(pair.getNameAsString().equals("path") || pair.getNameAsString().equals("value")) {
                    url = pair.getValue().toString().replaceAll("\"", "");
                }
            }
        } else if (endpointMapping instanceof SingleMemberAnnotationExpr) {
            url = endpointMapping.asSingleMemberAnnotationExpr().getMemberValue().toString().replaceAll("\"", "");
        } else if(endpointMapping instanceof MarkerAnnotationExpr) {
            if(preUrl.isEmpty()) {
                url = "/";
            }
        }

        if(finalHttpMethod == HttpMethod.ALL) {
            finalHttpMethod = httpFromMapping(endpointMapping.getNameAsString());
        }

        String finalURL = "";
        // Ensure preUrl starts with a slash if it exists
        if((!preUrl.isEmpty() && !preUrl.startsWith("/"))) {
            preUrl = "/" + preUrl;
        // Ensure Url starts with a slash if it exists
        } else if ((!url.isEmpty() && !url.startsWith("/"))) {
            url = "/" + url;
        }


        if(preUrl.isEmpty() && url.isEmpty()) {
            finalURL = "/";
        } else {
            finalURL = preUrl + url;
        }

        // Replace any double slashes
        finalURL = finalURL.replaceAll("//", "/");
        // If it ends with a slash remove it
        finalURL = finalURL.endsWith("/") && !finalURL.equals("/") ? finalURL.substring(0, finalURL.length() - 1) : finalURL;


        // Get query Parameters

        this.httpMethod = finalHttpMethod;
        this.name = endpointMapping.getNameAsString();
        this.url = simplifyEndpointURL(finalURL);
    }


    /**
     * Method to get http method from mapping
     * 
     * @param mapping mapping string for a given method
     * @return HttpMethod object of same method type
     */
    private static HttpMethod httpFromMapping(String mapping) {
        switch (mapping) {
            case "GetMapping":
            case "RequestMethod.GET":
                return HttpMethod.GET;
            case "PostMapping":
            case "RequestMethod.POST":
                return HttpMethod.POST;
            case "DeleteMapping":
            case "RequestMethod.DELETE":
                return HttpMethod.DELETE;
            case "PutMapping":
            case "RequestMethod.PUT":
                return HttpMethod.PUT;
            case "PatchMapping":
            case "RequestMethod.PATCH":
                return HttpMethod.PATCH;
            default:
                return HttpMethod.ALL;
        }

    }

    /**
     * Method to get endpoint path from annotations
     * 
     * @param ae annotation expression from method
     * @param url string formatted as a url
     * @return endpoint path/url from annotation expression
     */
    public static String getPathFromAnnotation(AnnotationExpr ae, String url) {
        // Annotations of type @Mapping("/endpoint")
        if (ae.isSingleMemberAnnotationExpr()) {
            url = url + StringParserUtils.simplifyEndpointURL(
                    StringParserUtils.removeOuterQuotations(
                            ae.asSingleMemberAnnotationExpr().getMemberValue().toString()));
        }

        // Annotations of type @Mapping(path="/endpoint")
        else if (ae.isNormalAnnotationExpr() && !ae.asNormalAnnotationExpr().getPairs().isEmpty()) {
            for (MemberValuePair mvp : ae.asNormalAnnotationExpr().getPairs()) {
                if (mvp.getName().toString().equals("path") || mvp.getName().toString().equals("value")) {
                    url = url + StringParserUtils.simplifyEndpointURL(
                            StringParserUtils.removeOuterQuotations(mvp.getValue().toString()));
                    break;
                }
            }
        }
        return url;
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
