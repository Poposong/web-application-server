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
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import db.DataBase;
import model.User;
import util.HttpRequestUtils;
import util.IOUtils;
/**
 * webserver에서 스레드를 동작시켰으므로 run() 메서드를 실행하게 됩니다.
 * 이때, 소켓을 통해 통신을 하기 위해 입출력스트림을 얻게 됩니다.
 * client에서 요청하는 http의 형태는 크게 3가지로 요청 라인, 요청 헤더, 요청 본문으로 나눌 수 있습니다.
 * 쿠키는 서버가 클라이언트에게 쿠키를 설정하면, 클라이언트는 이후 해당 도메인의 서버로 요청을 보낼 때마다 설정된 쿠키를 함께 보내게 됩니다. 클라이언트 측에서는 별도의 작업을 할 필요 없이 브라우저가 이를 자동으로 처리합니다.
 * */
public class RequestHandler extends Thread {
	private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

	private Socket connection;

	public RequestHandler(Socket socket) {
		this.connection = socket;
	}

	public void run() {
		log.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
				connection.getPort());

		try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
			BufferedReader br = new BufferedReader(new InputStreamReader(in));

			// [요청 라인] Ex) GET /index.html HTTP/1.1, GET /css/styles.css HTTP/1.1
			String line = br.readLine();

			// line이 null 값인 경우에 대한 예외 처리를 해야 한다. 아니면 무한 루프에 빠진다.
			if (line == null) {
				return;
			}

			String[] lines = line.split(" "); // 요청 라인을 [http-메소드, URI, http-버전]으로 나눈다.

			String url = lines[1]; // 요청 URI Ex) /index.html, /css/styles.css

			// [요청 헤더]를 분석한다.
			int contentLength = 0;
			boolean logined = false;
			while (!"".equals(line)) { // 헤더와 본문 사이의 빈 공백 라인을 발견하기 전까지 데이터를 읽어온다.
				line = br.readLine();
				if (line.contains("Content-Length")) {
					contentLength = contentLength(line);
				} else if (line.contains("Cookie")) {
					log.info("find : "+ line);
					logined = isLogin(line);
				}
			}

			// [요청 본문]에 대한 처리
			if (url.equals("/user/create")) { // 회원가입
				String body = IOUtils.readData(br, contentLength);

				Map<String, String> params = HttpRequestUtils.parseQueryString(body);

				User user = new User(params.get("userId"), params.get("password"), params.get("name"),
						params.get("email"));

				DataBase.addUser(user);

				DataOutputStream dos = new DataOutputStream(out);

				response302Header(dos, "/index.html");

			} else if (url.equals("/user/login")) { // 로그인
				String pathVariable = IOUtils.readData(br, contentLength);

				Map<String, String> params = HttpRequestUtils.parseQueryString(pathVariable);

				User user = DataBase.findUserById(params.get("userId"));

				if (user == null) {// DB에 회원의 정보가 없는 경우
					responseResource(out, "/user/login_failed.html");
					return;
				}

				if (user.getPassword().equals(params.get("password"))) { // DB에 저장한 비밀번호와 사용자가 입력한 비밀번호가 동일한 경우
					DataOutputStream dos = new DataOutputStream(out);
					response302LoginSuccessHeader(dos); // Header 요청 라인의 상태 코드를 302로 변경하고 Location을 추가한다.
				} else {
					responseResource(out, "/user/login_failed.html"); // 로그인에 실패한 경우
				}
			} else if (url.equals("/user/list")) { // 사용자의 목록을 조회한다. (로그인에 성공한 사용자들만 조회할 수 있다)
				if (!logined) {
					responseResource(out, "/user/login.html");
					return;
				}

				Collection<User> userList = DataBase.findAll();
				StringBuilder sb = new StringBuilder();
				sb.append("<table border='1'>");
				sb.append("<tr> <td>아이디</td> <td>이름</td> <td>이메일</td> </tr>");
				for (User user : userList) {
					sb.append("<tr>");
					sb.append("<td>" + user.getUserId() + "</td>");
					sb.append("<td>" + user.getName() + "</td>");
					sb.append("<td>" + user.getEmail() + "</td>");
					sb.append("</tr>");
				}
				sb.append("</table>");
				byte[] body = sb.toString().getBytes();
				DataOutputStream dos = new DataOutputStream(out);
				response200Header(dos, body.length);
				responseBody(dos, body);
			} else if (url.endsWith(".css")) { // css 적용하기
				DataOutputStream dos = new DataOutputStream(out);
				byte[] body = Files.readAllBytes(new File("./webapp" + url).toPath());
				response200CssHeader(dos, body.length);
				responseBody(dos, body);
			} else {
				responseResource(out, url);
			}

		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}

	// NOTE : content-Length의 길이를 반환하는 메소드
	public int contentLength(String line) {
		return Integer.parseInt(line.split(": ")[1]);
	}

	// NOTE : Cookie의 상태를 반환하는 메소드
	public boolean isLogin(String line) {
		String state = line.split(": ")[1].split("=")[1]; // line의 형태 → Cookie: logined=true
		return Boolean.parseBoolean(state);
	}

	// NOTE : Header의 상태 코드를 302으로 하고 url의 경로로 redirect 하는 메소드
	private void response302Header(DataOutputStream dos, String url) {
		try {
			dos.writeBytes("HTTP/1.1 302 Redirect \r\n");
			dos.writeBytes("Location: " + url + "\r\n");
			dos.writeBytes("\r\n");
		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}

	// NOTE : Header의 상태 코드를 200으로 하고 pathVariable의 길이를 반환하는 메소드
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

	// NOTE : 응답 body를 출력스트림에 쓰고 server로 HTTP header, body를 보낸다.
	private void responseBody(DataOutputStream dos, byte[] body) {
		try {
			dos.write(body, 0, body.length); // body를 출력스트림에 쓴다.
			dos.flush(); // 보낸다.
		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}

	// NOTE : HTTP header와 Body를 생성한다.
	private void responseResource(OutputStream out, String url) throws IOException {
		DataOutputStream dos = new DataOutputStream(out);
		byte[] body = Files.readAllBytes(new File("./webapp" + url).toPath()); //주어진 URL에서 파일을 읽어들여 바이트 배열로 반환합니다.
		response200Header(dos, body.length);
		responseBody(dos, body);
	}

	// NOTE : 사용자가 로그인에 실패했을 때 제공하는 http header
	private void response401LoginAuthorizedErrorHeader(DataOutputStream dos){
		try{
			dos.writeBytes("HTTP/1.1 401 Unauthorized \r\n");
			dos.writeBytes("Content-Length: 0 \r\n");
			dos.writeBytes("\r\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// NOTE : 로그인을 했으므로 Cookie의 상태를 true로 만들고 index.html로 redirect하는 메소드
	private void response302LoginSuccessHeader(DataOutputStream dos) {
		try {
			dos.writeBytes("HTTP/1.1 302 Redirect \r\n");
			dos.writeBytes("Set-Cookie: logined=true \r\n");
			dos.writeBytes("Location: /index.html \r\n");
			dos.writeBytes("\r\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// NOTE : Http Header를 생성하는 메소드
	private void response200CssHeader(DataOutputStream dos, int lengthOfBodyContent) {
		try {
			dos.writeBytes("HTTP/1.1 200 OK \r\n");
			dos.writeBytes("Content-Type: text/css\r\n");
			dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
			dos.writeBytes("\r\n");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
