package webserver;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import model.User;
import util.HttpRequestUtils;
import util.IOUtils;

public class RequestHandler extends Thread {
	private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

	private Socket connection;

	public RequestHandler(Socket connectionSocket) {
		this.connection = connectionSocket;
	}

	public void run() {
		log.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
				connection.getPort());

		try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
			// TODO 사용자 요청에 대한 처리는 이 곳에 구현하면 된다.

			// 1단계
			InputStreamReader isr = new InputStreamReader(in);

			BufferedReader br = new BufferedReader(isr);

			String line = br.readLine();

			// line이 null 값인 경우에 대한 예외 처리를 해야 한다. 아니면 무한 루프에 빠진다.
			if (line == null) {
				return;
			}
			
			System.out.println(line); // header 출력
			
			// 2단계
			String[] lines = line.split(" ");

			String url = lines[1];
 
			String[] root = url.split("/");
			
			
			if (root[1].equals("index.html")) { // TODO : GET /index.html
				DataOutputStream dos = new DataOutputStream(out);
				// 3단계
				byte[] body = Files.readAllBytes(new File("./webapp" + url).toPath()); // index.html의 태그가 body[]에
																						// 담긴다.
				response200Header(dos, body.length);
				responseBody(dos, body);
			} else if (root[1].equals("user")) { // TODO : GET /user/create?userId=yejin990508&password=1234&name=yejin
				// .split("?")[1]
				 
				if(root[2].equals("form.html")) { // TODO : POST /user/form.html
					System.out.println("come!!!!!");
				}else if(root[2].split("\\?")[0].equals("create")) {
					String path = root[2].split("\\?")[1];
					System.out.println("path : "+ path);
					Map<String, String> map = HttpRequestUtils.parseQueryString(path);

					User user = new User(map.get("userId"), map.get("password"), map.get("name"), "email");
					
					System.out.println(user.toString());
				}
				
			}
			
			
//
//			if (lines[0].equals("GET")) {
//
//				if (root.equals("index.html")) { // TODO : GET /index.html
//					DataOutputStream dos = new DataOutputStream(out);
//					// 3단계
//					byte[] body = Files.readAllBytes(new File("./webapp" + url).toPath()); // index.html의 태그가 body[]에
//																							// 담긴다.
//					response200Header(dos, body.length);
//					responseBody(dos, body);
//				} else if (root.equals("user")) { // TODO : GET user/create?userId=yejin990508&password=1234&name=yejin
//					// .split("?")[1]
//					String path = url.split("/")[2].split("\\?")[1];
//					Map<String, String> map = HttpRequestUtils.parseQueryString(path);
//
//					User user = new User(map.get("userId"), map.get("password"), map.get("name"), "email");
//					
//					System.out.println(user.toString());
//
//				}
//			} else if (lines[0].equals("POST")) {
//				if (root.equals("user")) { // TODO : POST /user/create
//					String move = url.split("/")[2]; // create
//					
//					int ContentLength = 0; // Content-Length
//					String path = ""; // userId=javaJig&password=password&name=JaeSung
//					
//					
//					while(!"".equals(line)) {
//						System.out.println(line);
//						
//						if(line.contains("?")) {
//							path = line.split("\\?")[1].split(" ")[0];
//							System.out.println("path : "+ path);
//						}else if(line.contains(":")) {
//							String[] str = line.split(": ");
//							if(str[0].equals("Content-Length")) {
//								ContentLength = Integer.parseInt(str[1]);
//							}
//						}
//						
//						
//						line = br.readLine();
//
//					}
//		
//				}
//			}

		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}

	private void response200Header(DataOutputStream dos, int lengthOfBodyContent) {
		try {
			dos.writeBytes("HTTP/1.1 200 OK \r\n");
			dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
			dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
			dos.writeBytes("\r\n");
		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}

	private void responseBody(DataOutputStream dos, byte[] body) {
		try {
			dos.write(body, 0, body.length);
			dos.flush();
		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}
}
