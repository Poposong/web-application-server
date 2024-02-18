package util;

import java.io.BufferedReader;
import java.io.IOException;

public class IOUtils {
    // 매개변수 br의 값은 Request Body를 시작하는 시점이고 contentLength는 Request Header의 Content-Length 값이다.
    public static String readData(BufferedReader br, int contentLength) throws IOException {
        char[] body = new char[contentLength];
        br.read(body, 0, contentLength);  // br을 읽어서 body에 0부터 contentLength 까지 저장한다.
        return String.copyValueOf(body);
    }
}
