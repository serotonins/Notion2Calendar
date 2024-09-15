package com.example.notion2calendar;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class NotionController {

    private final NotionService notionService;

    @GetMapping("/fetch-notion-events")
    public List<Map<String, Object>> fetchNotionEvents() {
        return notionService.fetchNotionData();
    }

    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private static final String CREDENTIALS_FILE_PATH = "/static/nep_credential.json"; // 구글 API 인증 파일 경로

    private Credential credential;

    @GetMapping("/")
    public ModelAndView index() {
        return new ModelAndView("index");
    }

    @GetMapping("/login")
    public ModelAndView login() throws Exception {
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
                new InputStreamReader(getClass().getResourceAsStream(CREDENTIALS_FILE_PATH)));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, clientSecrets,
                Collections.singleton("https://www.googleapis.com/auth/calendar"))
                .setAccessType("offline")
                .build();

        String redirectUri = "http://localhost:8080/oauth2/callback";
        String authUrl = flow.newAuthorizationUrl().setRedirectUri(redirectUri).build();

        return new ModelAndView("redirect:" + authUrl);
    }

    @GetMapping("/oauth2/callback")
    public ModelAndView oauth2Callback(@RequestParam("code") String code) throws Exception {
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
                new InputStreamReader(getClass().getResourceAsStream(CREDENTIALS_FILE_PATH)));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, clientSecrets,
                Collections.singleton("https://www.googleapis.com/auth/calendar"))
                .setAccessType("offline")
                .build();

        // GoogleTokenResponse를 가져옵니다.
        GoogleTokenResponse tokenResponse = flow.newTokenRequest(code)
                .setRedirectUri("http://localhost:8080/oauth2/callback")
                .execute();

        // Credential로 변환합니다.
        credential = flow.createAndStoreCredential(tokenResponse, "user");

        if (credential == null) {
            throw new Exception("Failed to create and store credential.");
        } else {
            System.out.println("Credential stored: " + credential.getAccessToken());
            System.out.println(credential);
        }

        // 여기서 액세스 토큰을 사용하여 Google Calendar API를 호출할 수 있습니다.
        return new ModelAndView("success");
    }

    @GetMapping("/sync-notion-to-google-calendar")
    public String syncNotionEvents() {
        try {
            List<Map<String, Object>> notionEvents = notionService.fetchNotionData(); // 노션 데이터 가져오기

            // 여기서 Credential 객체를 가져오는 로직 추가 (OAuth 인증)
            Credential credential = getCredential(); // 인증 로직 구현 필요

            notionService.addEventsToGoogleCalendar(notionEvents, credential); // 구글 캘린더에 이벤트 추가

            return "Events synced successfully!";
        } catch (Exception e) {
            return "Error syncing events: " + e.getMessage();
        }
    }

    private Credential getCredential() throws Exception {
        if (credential == null) {
            throw new Exception("Credential not found in memory.");
        }
        return credential;
    }

//    ~이건 credential을 파일에 저장하는 방식~
//    private Credential getCredential() throws Exception {
//        // OAuth 2.0 인증 로직을 구현하여 Credential 객체를 반환합니다.
//        // 인증 파일을 읽고 GoogleAuthorizationCodeFlow를 설정합니다.
//        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
//                new InputStreamReader(getClass().getResourceAsStream(CREDENTIALS_FILE_PATH)));
//
//        System.out.println("clientSecrets! " + clientSecrets);
//
//        // NetHttpTransport 인스턴스를 명시적으로 생성
//        NetHttpTransport httpTransport;
//        try {
//            httpTransport = GoogleNetHttpTransport.newTrustedTransport();
//        } catch (Exception e) {
//            System.err.println("Error creating HTTP transport: " + e.getMessage());
//            throw e; // 예외를 다시 던져서 상위에서 처리하도록
//        }
//
//        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
//                httpTransport, JSON_FACTORY, clientSecrets,
//                Collections.singleton("https://www.googleapis.com/auth/calendar"))
//                .setAccessType("offline")
//                .build();
//
//        System.out.println("flow! " + flow + "\n" + flow.loadCredential("user"));
//
//        // 사용자 인증을 위한 로직 구현 필요
//        // 예: Redirect URI를 통해 사용자 인증을 받고, 토큰을 저장하여 Credential을 반환
//        return flow.loadCredential("user"); // 사용자 ID에 맞는 Credential 반환
//    }

    @GetMapping("/why")
    public String why() {
        return "Whay";
    }

    @GetMapping("/add-test-event")
    public String addTestEvent() {
        try {
            // 여기서 Credential 객체를 가져오는 로직 추가 (OAuth 인증)
            Credential credential = getCredential(); // 인증 로직 구현 필요
            System.out.println("controller credential! " + credential);

            notionService.addTestEventToGoogleCalendar(credential); // 테스트 이벤트 추가

            return "Test event added successfully!";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error adding test event: " + e.getMessage();
        }
    }
}
