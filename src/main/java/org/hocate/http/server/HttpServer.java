package org.hocate.http.server;

import java.io.IOException;

import org.hocate.http.server.websocket.WebSocketDispatcher;
import org.hocate.http.server.websocket.WebSocketHandler;
import org.hocate.network.aio.AioServerSocket;
import org.hocate.network.messageParter.HttpMessageParter;

/**
 * HttpServer 对象
 * 
 * @author helyho
 * 
 */
public class HttpServer {
	private AioServerSocket		aioServerSocket;
	private HttpDispatcher	requestDispatcher;
	private WebSocketDispatcher webSocketDispatcher;

	/**
	 * 构造函数
	 * 
	 * @param host
	 *            监听地址
	 * @param port
	 *            监听端口
	 * @param timeout
	 *            超时时间
	 * @param rootDir
	 *            根目录
	 * @throws IOException
	 *             异常
	 */
	public HttpServer(WebConfig config) throws IOException {

		// 准备 socket 监听
		aioServerSocket = new AioServerSocket(config.getHost(), config.getPort(), config.getTimeout());
		this.requestDispatcher = new HttpDispatcher(config);
		this.webSocketDispatcher = new WebSocketDispatcher(config);
		aioServerSocket.handler(new HttpServerHandler(config, requestDispatcher,webSocketDispatcher));
		aioServerSocket.filterChain().add(new HttpServerFilter());
		aioServerSocket.messageParter(new HttpMessageParter());
	}

	/**
	 * 以下是一些 HTTP 方法的成员函数
	 */

	public void get(String routeRegexPath, HttpHandler handler) {
		requestDispatcher.addRouteHandler("GET", "^" + routeRegexPath + "$", handler);
	}

	public void post(String routeRegexPath, HttpHandler handler) {
		requestDispatcher.addRouteHandler("POST", "^" + routeRegexPath + "$", handler);
	}

	public void head(String routeRegexPath, HttpHandler handler) {
		requestDispatcher.addRouteHandler("HEAD", "^" + routeRegexPath + "$", handler);
	}

	public void put(String routeRegexPath, HttpHandler handler) {
		requestDispatcher.addRouteHandler("PUT", "^" + routeRegexPath + "$", handler);
	}

	public void delete(String routeRegexPath, HttpHandler handler) {
		requestDispatcher.addRouteHandler("delete", "^" + routeRegexPath + "$", handler);
	}

	public void trace(String routeRegexPath, HttpHandler handler) {
		requestDispatcher.addRouteHandler("TRACE", "^" + routeRegexPath + "$", handler);
	}

	public void connect(String routeRegexPath, HttpHandler handler) {
		requestDispatcher.addRouteHandler("CONNECT", "^" + routeRegexPath + "$", handler);
	}

	public void options(String routeRegexPath, HttpHandler handler) {
		requestDispatcher.addRouteHandler("OPTIONS", "^" + routeRegexPath + "$", handler);
	}

	public void otherMethod(String method, String routeRegexPath, HttpHandler handler) {
		requestDispatcher.addRouteMethod(method);
		requestDispatcher.addRouteHandler(method, "^" + routeRegexPath + "$", handler);
	}
	
	public void socket(String routeRegexPath, WebSocketHandler handler) {
		webSocketDispatcher.addRouteHandler(routeRegexPath, handler);
	}
	

	/**
	 * 构建新的 HttpServer,从配置文件读取配置
	 * 
	 * @return
	 */
	public static HttpServer newInstance() {
		try {
			return new HttpServer(WebContext.getWebConfig());
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * 启动服务
	 * 
	 * @throws IOException
	 */
	public void Serve() throws IOException {
		aioServerSocket.start();
	}
}
