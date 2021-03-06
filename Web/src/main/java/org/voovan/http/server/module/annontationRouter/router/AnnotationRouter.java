package org.voovan.http.server.module.annontationRouter.router;

import org.voovan.http.HttpContentType;
import org.voovan.http.message.HttpStatic;
import org.voovan.http.server.*;
import org.voovan.http.server.exception.AnnotationRouterException;
import org.voovan.http.server.module.annontationRouter.AnnotationModule;
import org.voovan.http.server.module.annontationRouter.annotation.*;
import org.voovan.http.websocket.WebSocketRouter;
import org.voovan.tools.TEnv;
import org.voovan.tools.TFile;
import org.voovan.tools.TString;
import org.voovan.tools.json.JSON;
import org.voovan.tools.log.Logger;
import org.voovan.tools.reflect.TReflect;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 通过注解实现的路由
 *
 * @author: helyho
 * Voovan Framework.
 * WebSite: https://github.com/helyho/Voovan
 * Licence: Apache v2 License
 */
public class AnnotationRouter implements HttpRouter {

    private static Map<Class, Object> singletonObjs = new ConcurrentHashMap<Class, Object>();

    private String urlPath;
    private String paramPath;
    private String path;
    private Class clazz;
    private Method method;
    private String methodName;
    private Router classRouter;
    private Router methodRoute;
    private AnnotationModule annotationModule;

    /**
     * 构造函数
     * @param annotationModule AnnotationModule 对象
     * @param clazz   Class对象
     * @param method  方法对象
     * @param classRouter 类上的 Route 注解
     * @param methodRoute 方法上的 Route 注解
     * @param urlPath url 路径
     * @param paramPath 带参数的 url 路径
     */
    public AnnotationRouter(AnnotationModule annotationModule, Class clazz, Method method, Router classRouter, Router methodRoute, String urlPath, String paramPath) {
        this.annotationModule = annotationModule;
        this.clazz = clazz;
        this.method = method;
        this.methodName = method.getName();
        this.classRouter = classRouter;
        this.methodRoute = methodRoute;
        this.urlPath = urlPath;
        this.paramPath = paramPath;
        this.path = urlPath + paramPath;

        annotationModule.METHOD_URL_MAP.put(method, urlPath);
        annotationModule.URL_METHOD_MAP.put(urlPath, method);
        //如果是单例,则进行预实例化
        if(classRouter.singleton() && !singletonObjs.containsKey(clazz)){
            try {
                singletonObjs.put(clazz, clazz.newInstance());
            } catch (Exception e) {
                Logger.error("New a singleton object error", e);
            }
        }
    }

    public String getUrlPath() {
        return urlPath;
    }

    public String getParamPath() {
        return paramPath;
    }

    public String getPath() {
        return path;
    }

    public Class getClazz() {
        return clazz;
    }

    public Method getMethod() {
        return method;
    }

    public Router getClassRouter() {
        return classRouter;
    }

    public Router getMethodRoute() {
        return methodRoute;
    }

    public AnnotationModule getAnnotationModule() {
        return annotationModule;
    }

    /**
     * 扫描包含Router注解的类
     *
     * @param annotationModule   AnnotationModule对象用于注册路由
     */
    public static void scanRouterClassAndRegister(AnnotationModule annotationModule) {
        int routeMethodNum = 0;

        String modulePath = annotationModule.getModuleConfig().getPath();
        modulePath = HttpDispatcher.fixRoutePath(modulePath);

        WebServer webServer = annotationModule.getWebServer();
        try {
            //查找包含 Router 注解的类
            String[] scanRouterPackageArr = annotationModule.getScanRouterPackage().split(";");
            for(String scanRouterPackage : scanRouterPackageArr) {
                scanRouterPackage = scanRouterPackage.trim();

                List<Class> routerClasses = TEnv.searchClassInEnv(scanRouterPackage, new Class[]{Router.class});
                for (Class routerClass : routerClasses) {
                    Method[] methods = routerClass.getMethods();
                    Router[] annonClassRouters = (Router[]) routerClass.getAnnotationsByType(Router.class);

                    //多个 Router 注解的迭代
                    for (Router annonClassRouter : annonClassRouters) {
                        String classRouterPath = annonClassRouter.path().isEmpty() ? annonClassRouter.value() : annonClassRouter.path();
                        String[] classRouterMethods = annonClassRouter.method();

                        //多个请求方法的迭代
                        for (String classRouterMethod : classRouterMethods) {

                            //使用类名指定默认路径
                            if (classRouterPath.isEmpty()) {
                                //使用类名指定默认路径
                                classRouterPath = routerClass.getSimpleName();
                            }

                            classRouterPath = HttpDispatcher.fixRoutePath(classRouterPath);

                            //注册以采用静态方法低啊用
//                        if(methods.length > 0) {
//                            TReflect.register(routerClass);
//                        }

                            //扫描包含 Router 注解的方法
                            for (Method method : methods) {
                                Router[] annonMethodRouters = (Router[]) method.getAnnotationsByType(Router.class);
                                if (annonMethodRouters != null) {

                                    //多个 Router 注解的迭代, 一个方法支持多个路由
                                    for (Router annonMethodRouter : annonMethodRouters) {
                                        String methodRouterPath = annonMethodRouter.path().isEmpty() ? annonMethodRouter.value() : annonMethodRouter.path();
                                        String[] methodRouterMethods = annonMethodRouter.method();

                                        //多个请求方法的迭代,  一个路由支持多个 Http mehtod
                                        for (String methodRouterMethod : methodRouterMethods) {

                                            //使用方法名指定默认路径
                                            if (methodRouterPath.isEmpty()) {
                                                //如果方法名为: index 则为默认路由
                                                if (method.getName().equals("index")) {
                                                    methodRouterPath = "/";
                                                } else {
                                                    methodRouterPath = method.getName();
                                                }
                                            }

                                            //拼装方法路径
                                            methodRouterPath = HttpDispatcher.fixRoutePath(methodRouterPath);

                                            //拼装 (类+方法) 路径
                                            String routePath = classRouterPath + methodRouterPath;

                                            //如果方法上的注解指定了 Method 则使用方法上的注解指定的,否则使用类上的注解指定的
                                            String routeMethod = methodRouterMethod.isEmpty() ? classRouterMethod : methodRouterMethod;
                                            routeMethod = routeMethod.isEmpty() ? HttpStatic.GET_STRING : routeMethod;

                                            //为方法的参数准备带参数的路径
                                            String paramPath = "";
                                            Annotation[][] paramAnnotationsArrary = method.getParameterAnnotations();
                                            Class[] paramTypes = method.getParameterTypes();
                                            for (int i = 0; i < paramAnnotationsArrary.length; i++) {
                                                Annotation[] paramAnnotations = paramAnnotationsArrary[i];

                                                if (paramAnnotations.length == 0 &&
                                                        paramTypes[i] != HttpRequest.class &&
                                                        paramTypes[i] != HttpResponse.class &&
                                                        paramTypes[i] != HttpSession.class) {
                                                    paramPath = paramPath + "/:param" + (i + 1);
                                                    continue;
                                                }

                                                for (Annotation paramAnnotation : paramAnnotations) {
                                                    if (paramAnnotation instanceof Param) {
                                                        paramPath = TString.assembly(paramPath, "/:", ((Param) paramAnnotation).value());
                                                    }

                                                    //如果没有指定方法, 参数包含 BodyParam 注解则指定请求方法为 POST
                                                    if ((paramAnnotation instanceof BodyParam || paramAnnotation instanceof Body) && routeMethod.equals(HttpStatic.GET_STRING)) {
                                                        routeMethod = HttpStatic.POST_STRING;
                                                    }
                                                }
                                            }

                                            /**
                                             * 注册路由部分代码在下面
                                             */
                                            if (webServer.getHttpRouters().get(routeMethod) == null) {
                                                webServer.getHttpDispatcher().addRouteMethod(routeMethod);
                                            }

                                            //生成完整的路由,用来检查路由是否存在
                                            routePath = HttpDispatcher.fixRoutePath(routePath);

                                            //这里这么做是为了处理 TreeMap 的 containsKey 方法的 bug
                                            Map routerMaps = new HashMap();
                                            routerMaps.putAll(webServer.getHttpRouters().get(routeMethod));

                                            //构造注解路由器
                                            AnnotationRouter annotationRouter = new AnnotationRouter(annotationModule, routerClass, method,
                                                    annonClassRouter, annonMethodRouter, routePath, paramPath);

                                            String routeLog = null;

                                            //1.注册路由, 处理不带参数的路由
                                            routePath = HttpDispatcher.fixRoutePath(routePath);
                                            String moduleRoutePath = HttpDispatcher.fixRoutePath(modulePath + routePath);
                                            //判断路由是否注册过
                                            if (!routerMaps.containsKey(moduleRoutePath)) {
                                                //注册路由,不带路径参数的路由
                                                annotationModule.otherMethod(routeMethod, routePath, annotationRouter);
                                                routeLog = "[SYSTEM] Module [" + annotationModule.getModuleConfig().getName() +
                                                        "] add Router: " + TString.rightPad(routeMethod, 8, ' ') +
                                                        moduleRoutePath;
                                                routeMethodNum++;
                                            }

                                            //2.注册路由,带路径参数的路由
                                            if(!paramPath.isEmpty()) {
                                                String routeParamPath = null;
                                                routeParamPath = routePath + paramPath;
                                                routeParamPath = HttpDispatcher.fixRoutePath(routeParamPath);
                                                String moduleRouteParamPath = HttpDispatcher.fixRoutePath(modulePath + routeParamPath);

                                                if (!routerMaps.containsKey(moduleRoutePath)) {
                                                    annotationModule.otherMethod(routeMethod, routeParamPath, annotationRouter);

                                                    routeLog = "[SYSTEM] Module [" + annotationModule.getModuleConfig().getName() +
                                                            "] add Router: " + TString.rightPad(routeMethod, 8, ' ') +
                                                            moduleRouteParamPath;
                                                }
                                            }

                                            if(routeLog!=null) {
                                                Logger.simple(routeLog);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                //查找包含 WebSocket 注解的类
                List<Class> webSocketClasses = TEnv.searchClassInEnv(annotationModule.getScanRouterPackage(), new Class[]{WebSocket.class});
                for (Class webSocketClass : webSocketClasses) {
                    if (TReflect.isExtendsByClass(webSocketClass, WebSocketRouter.class)) {
                        WebSocket[] annonClassRouters = (WebSocket[]) webSocketClass.getAnnotationsByType(WebSocket.class);
                        WebSocket annonClassRouter = annonClassRouters[0];
                        String classRouterPath = annonClassRouter.path().isEmpty() ? annonClassRouter.value() : annonClassRouter.path();

                        //使用类名指定默认路径
                        if (classRouterPath.isEmpty()) {
                            //使用类名指定默认路径
                            classRouterPath = webSocketClass.getSimpleName();
                        }

                        classRouterPath = HttpDispatcher.fixRoutePath(classRouterPath);
                        String moduleRoutePath = HttpDispatcher.fixRoutePath(modulePath + classRouterPath);

                        if (!webServer.getWebSocketRouters().containsKey(moduleRoutePath)) {
                            annotationModule.socket(classRouterPath, (WebSocketRouter) TReflect.newInstance(webSocketClass));
                            Logger.simple("[SYSTEM] Module [" + annotationModule.getModuleConfig().getName() +
                                    "] add WebSocket: " + TString.leftPad(moduleRoutePath, 11, ' '));
                            routeMethodNum++;
                        }
                    }
                }

                if(routeMethodNum>0) {
                    Logger.simple(TFile.getLineSeparator() + "[SYSTEM] Module [" + annotationModule.getModuleConfig().getName() +
                            "] Scan some class [" + scanRouterPackage + "] annotation by Router: " + routerClasses.size() +
                            ". Register Router method annotation by route: " + routeMethodNum + ".");
                }
            }

        } catch (Exception e){
            Logger.error("Scan router class error.", e);
        }
    }

//    /**
//     * 修复路由路径
//     * @param routePath 路由路径
//     * @return 修复后的路由路径
//     */
//    private static String fixAnnotationRoutePath(String routePath){
//        routePath = routePath.startsWith("/") ? TString.removePrefix(routePath) : routePath;
//        routePath = routePath.endsWith("/") ? TString.removeSuffix(routePath) : routePath;
//        return routePath;
//    }

    /**
     * 将一个 Http 请求映射到一个类的方法调用
     * @param request   http 请求对象
     * @param response  http 响应对象
     * @param clazz     Class 对象
     * @param method    Method 对象
     * @return  返回值
     * @throws Exception 调用过程中的异常
     */
    public Object invokeRouterMethod(HttpRequest request, HttpResponse response, Class clazz, Method method) throws Exception {

        Object annotationObj = null;

        //如果是单例模式则使用预先初始话好的
        if(this.classRouter.singleton()){
            annotationObj = singletonObjs.get(clazz);
        } else {
            annotationObj = clazz.newInstance();
        }

        Class[] parameterTypes = method.getParameterTypes();
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();

        String bodyString = "";
        Map bodyMap = null;
        if(request.body().size() > 0) {
            bodyString = request.body().getBodyString();
            if(JSON.isJSONMap(bodyString)) {
                bodyMap = (Map) JSON.parse(bodyString);
            }
        }

        //准备参数
        Object[] params = new Object[parameterTypes.length];
        for(int i=0; i < parameterAnnotations.length; i++){

            //请求对象
            if(parameterTypes[i] == HttpRequest.class){
                params[i] = request;
                continue;
            }

            //响应对象
            if(parameterTypes[i] == HttpResponse.class){
                params[i] = response;
                continue;
            }

            //会话对象
            if(parameterTypes[i] == HttpSession.class){
                params[i] = request.getSession();
                continue;
            }

            for(Annotation annotation : parameterAnnotations[i]) {

                //请求的参数
                if (annotation instanceof Param) {
                    String paramName = ((Param) annotation).value();
                    try {
                        params[i] = TString.toObject(request.getParameter(paramName), parameterTypes[i], true);
                        continue;
                    } catch (Exception e) {
                        if(((Param) annotation).isRequire()) {
                            throw new AnnotationRouterException("Router annotation @Param [" + paramName + " = " + params[i] + "] error, data: " + request.getParameters(), e);
                        }
                    }
                }

                //请求的参数
                if (annotation instanceof BodyParam) {
                    String paramName = ((BodyParam) annotation).value();
                    try {
                        if(bodyMap != null && bodyMap instanceof Map) {
                            Object bodyParam = bodyMap.get(paramName);
                            if(TReflect.isBasicType(bodyParam.getClass())) {
                                params[i] = TString.toObject(bodyParam.toString(), parameterTypes[i], true);
                            } else if(bodyParam instanceof Map){
                                params[i] = TReflect.getObjectFromMap(parameterTypes[i], (Map)bodyParam, true);
                            } else {
                                params[i] = bodyParam;
                            }
                        }
                        continue;
                    } catch (Exception e) {
                        if(((BodyParam) annotation).isRequire()) {
                            throw new AnnotationRouterException("Router annotation @BodyParam [" + paramName + " = " + params[i] + "] error, data: " + bodyMap.toString(), e);
                        }
                    }

                }

                //请求的头
                if (annotation instanceof Header) {
                    String headName = ((Header) annotation).value();
                    try {
                        params[i] = TString.toObject(request.header().get(headName), parameterTypes[i], true);
                        continue;
                    } catch (Exception e) {
                        if(((Header) annotation).isRequire()) {
                            throw new AnnotationRouterException("Router annotation @Head [" + headName + " = " + params[i] + "] error, data: " + request.header().toString(), e);
                        }
                    }
                }

                //请求的 Cookie
                if (annotation instanceof Cookie) {
                    String cookieValue = null;
                    String cookieName = ((Cookie) annotation).value();
                    try {
                        org.voovan.http.message.packet.Cookie cookie = request.getCookie(cookieName);
                        if (cookie != null) {
                            cookieValue = cookie.getValue();
                        }

                        params[i] = TString.toObject(cookieValue, parameterTypes[i], true);
                        continue;
                    } catch (Exception e) {
                        if(((Cookie) annotation).isRequire()) {
                            String cookieStr = request.cookies().parallelStream().map(cookie -> cookie.getName() + "=" + cookie.getName()).collect(Collectors.toList()).toString();
                            throw new AnnotationRouterException("Router annotation @Cookie [" + cookieName + " = " + params[i] + "] error, data: " + cookieStr, e);
                        }
                    }
                }

                //请求的 Body
                if (annotation instanceof Body) {
                    try {
                        params[i] = bodyMap == null ?
                                TString.toObject(bodyString, parameterTypes[i], true) :
                                TReflect.getObjectFromMap(parameterTypes[i], bodyMap, true);
                        continue;
                    } catch (Exception e) {
                        if(((Body) annotation).isRequire()) {
                            throw new AnnotationRouterException("Router annotation @Body error \r\n data: " + bodyString, e);
                        }
                    }
                }

                //请求的头
                if (annotation instanceof Attribute) {
                    String attrName = ((Attribute) annotation).value();
                    try {
                        params[i] = TString.toObject(request.getAttributes().get(attrName).toString(), parameterTypes[i], true);
                        continue;
                    } catch (Exception e) {
                        if(((Attribute) annotation).isRequire()) {
                            throw new AnnotationRouterException("Router annotation @Attribute [" + attrName + " = " + params[i] + "] error, data: " + request.header().toString(), e);
                        }
                    }
                }

                //请求的头
                if (annotation instanceof Session) {
                    String sessionName = ((Session) annotation).value();
                    HttpSession httpSession = request.getSession();

                    try {
                        if (httpSession.getAttribute(sessionName).getClass() == parameterTypes[i]) {
                            params[i] = httpSession.getAttribute(sessionName);
                        }
                        continue;
                    } catch (Exception e) {
                        if(((Session) annotation).isRequire()) {
                            throw new AnnotationRouterException("Router annotation @Session [" + sessionName + " = " + params[i] + "] error, data: " + httpSession.attributes().toString(), e);
                        }
                    }
                }
            }

            //没有注解的参数,按顺序处理
            if(params[i]==null) {
                try {
                    String value = request.getParameter("param" + String.valueOf(i + 1));
                    params[i] = TString.toObject(value, parameterTypes[i], true);
                    continue;
                } catch (Exception e) {
                    throw new AnnotationRouterException("Router sequential injection param " + request.getParameters().toString() + " error", e);
                }
            }

        }

        //调用方法
        return TReflect.invokeMethod(annotationObj, method, params);
    }

    @Override
    public void process(HttpRequest request, HttpResponse response) throws Exception {
        AnnotationRouterFilter annotationRouterFilter = annotationModule.getAnnotationRouterFilter();

        Object responseObj = null;
        Object fliterResult = null;

        try {
            //根据 Router 注解的标记设置响应的Content-Type
            response.header().put(HttpStatic.CONTENT_TYPE_STRING, HttpContentType.getHttpContentType(methodRoute.contentType()));

            //过滤器前置处理
            if(annotationRouterFilter!=null) {
                fliterResult = annotationRouterFilter.beforeInvoke(request, response, this);
            }

            //null: 执行请求路由方法
            //非 null: 作为 http 请求的响应直接返回
            if(fliterResult == null) {
                responseObj = invokeRouterMethod(request, response, clazz, method);
            } else {
                responseObj = fliterResult;
            }

            //过滤器后置处理
            if(annotationRouterFilter!=null) {
                fliterResult = annotationRouterFilter.afterInvoke(request, response, this, responseObj);
                if(fliterResult!=null) {
                    responseObj = fliterResult;
                }
            }
        } catch(Exception e) {
            //过滤器拦截异常
            if(annotationRouterFilter!=null) {
                fliterResult = annotationRouterFilter.exception(request, response, this, e);
            }

            if(fliterResult !=null) {
                responseObj = fliterResult;
            } else {
                if (e.getCause() != null) {
                    Throwable cause = e.getCause();
                    if (cause instanceof Exception) {
                        e = (Exception) cause;
                    }
                }

                Logger.error(e);

                if (e instanceof AnnotationRouterException) {
                    throw e;
                } else {
                    throw new AnnotationRouterException("Process annotation router error. URL: " + request.protocol().getPath(), e);
                }
            }
        }

        if (responseObj != null && response.body().size() == 0) {
            if (responseObj instanceof String) {
                response.write((String) responseObj);
            } else if (responseObj instanceof byte[]) {
                response.write((byte[]) responseObj);
            } else {
                response.header().put(HttpStatic.CONTENT_TYPE_STRING, HttpContentType.getHttpContentType(HttpContentType.JSON));
                response.write(JSON.toJSON(responseObj));
            }
        }
    }
}
