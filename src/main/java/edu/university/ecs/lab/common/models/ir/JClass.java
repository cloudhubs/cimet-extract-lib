package edu.university.ecs.lab.common.models.ir;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import edu.university.ecs.lab.common.models.enums.ClassRole;
import edu.university.ecs.lab.common.models.enums.FileType;
import edu.university.ecs.lab.common.models.serialization.JsonSerializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a class in Java. It holds all information regarding that class including all method
 * declarations, method calls, fields, etc.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class JClass extends ProjectFile implements JsonSerializable {
    private String packageName;

    /**
     * Class implementations
     */
    private Set<String> implementedTypes;

    /**
     * Role of the class in the microservice system. See {@link ClassRole}
     */
    private ClassRole classRole;

    /**
     * Set of methods in the class
     */
    private Set<Method> methods;

    /**
     * Set of class fields
     */
    private Set<Field> fields;

    /**
     * Set of class level annotations
     */
    private Set<Annotation> annotations;

    /**
     * List of method invocations made from within this class
     */
    private List<MethodCall> methodCalls;


    public JClass(String name, String path, String packageName, ClassRole classRole) {
        this.name = name;
        this.packageName = packageName;
        this.path = path;
        this.classRole = classRole;
        this.methods = new HashSet<>();
        this.fields = new HashSet<>();
        this.annotations = new HashSet<>();
        this.methodCalls = new ArrayList<>();
        this.implementedTypes = new HashSet<>();
        this.fileType = FileType.JCLASS;
    }

    public JClass(String name, String path, String packageName, ClassRole classRole, Set<Method> methods, Set<Field> fields, Set<Annotation> classAnnotations, List<MethodCall> methodCalls, Set<String> implementedTypes) {
        this.name = name;
        this.packageName = packageName;
        this.path = path;
        this.classRole = classRole;
        this.methods = methods;
        this.fields = fields;
        this.annotations = classAnnotations;
        this.methodCalls = methodCalls;
        this.implementedTypes = implementedTypes;
        this.fileType = FileType.JCLASS;
    }


    /**
     * see {@link JsonSerializable#toJsonObject()}
     */
    @Override
    public JsonObject toJsonObject() {
        JsonObject jsonObject = super.toJsonObject();
        Gson gson = new Gson();

        jsonObject.addProperty("packageName", getPackageName());
        jsonObject.addProperty("classRole", getClassRole().name());
        jsonObject.add("annotations", JsonSerializable.toJsonArray(getAnnotations()));
        jsonObject.add("fields", JsonSerializable.toJsonArray(getFields()));
        jsonObject.add("methods", JsonSerializable.toJsonArray(getMethods()));
        jsonObject.add("methodCalls", JsonSerializable.toJsonArray(getMethodCalls()));
        jsonObject.add("implementedTypes", gson.toJsonTree(getImplementedTypes()).getAsJsonArray());

        return jsonObject;
    }

    /**
     * This method returns all endpoints found in the methods of this class,
     * grouped under the same list as an Endpoint is an extension of a Method
     * see {@link Endpoint}
     * @return set of all endpoints
     */
    public Set<Endpoint> getEndpoints() {
        if((!getClassRole().equals(ClassRole.CONTROLLER) && !getClassRole().equals(ClassRole.REP_REST_RSC)) || getMethods().isEmpty()) {
            return new HashSet<>();
        }
        return methods.stream().filter(method -> method instanceof Endpoint).map(method -> (Endpoint) method).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * This method returns all restCalls found in the methodCalls of this class,
     * grouped under the same list as an RestCall is an extension of a MethodCall
     * see {@link RestCall}
     * @return set of all restCalls
     */
    public List<RestCall> getRestCalls() {

        return methodCalls.stream().filter(methodCall -> methodCall instanceof RestCall).map(methodCall -> (RestCall) methodCall).collect(Collectors.toUnmodifiableList());
    }

    /**
     * If we are adding a class or a class is being adopted/orphanized lets update ms name
     *
     * @param name
     */
    public void updateMicroserviceName(String name) {
        methodCalls.forEach(methodCall -> methodCall.setMicroserviceName(name));
        methods.forEach(methodCall -> methodCall.setMicroserviceName(name));
    }
}
