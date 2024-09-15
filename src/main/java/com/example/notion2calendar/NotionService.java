package com.example.notion2calendar;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class NotionService {

    @Value("${NOTION_API_KEY}")
    private String NOTION_API_KEY; // 노션 API 키

    @Value("${NOTION_DATABASE_ID}")
    private String NOTION_DATABASE_ID;

    private String NOTION_URL = "https://api.notion.com/v1/databases/" + NOTION_DATABASE_ID + "/query"; // 데이터베이스 ID

    @PostConstruct
    public void init() {
        this.NOTION_URL = "https://api.notion.com/v1/databases/" + NOTION_DATABASE_ID + "/query";
    }

    private static final String APPLICATION_NAME = "notion2googlecalendar";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();


    public List<Map<String, Object>> fetchNotionData() {
        LocalDate today = LocalDate.now(); // 오늘 날짜

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + NOTION_API_KEY);
        headers.set("Content-Type", "application/json");
        headers.set("Notion-Version", "2022-06-28");

        HttpEntity<String> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<Map> response = restTemplate.exchange(NOTION_URL, HttpMethod.POST, entity, Map.class);

        if (response.getStatusCodeValue() != 200) {
            throw new RuntimeException("Failed to fetch data from Notion: " + response.getStatusCode());
        }

        List<Map<String, Object>> results = (List<Map<String, Object>>) response.getBody().get("results");
        List<Map<String, Object>> filteredEvents = new ArrayList<>();
        List<Map<String, Object>> blankList = new ArrayList<>();


        for (Map<String, Object> event : results) {
            Map<String, Object> properties = (Map<String, Object>) event.get("properties");
            Map<String, Object> dateProperty = (Map<String, Object>) properties.get("날짜");

            if (dateProperty != null) {
                Map<String, String> dateDetails = (Map<String, String>) dateProperty.get("date");
                if (dateDetails != null) {
                    String startTime = dateDetails.get("start");
                    String endTime = dateDetails.get("end");

                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

                    LocalDate dateTime = null;

                    try {
                        // 첫 번째 형식: yyyy-MM-dd
                        dateTime = LocalDate.parse(startTime, formatter);
                    } catch (DateTimeParseException e1) {
                        try {
                            // 두 번째 형식: yyyy-MM-ddTHH:mm:ss.SSSZZZZZ
                            OffsetDateTime offsetDateTime = OffsetDateTime.parse(startTime);
                            dateTime = offsetDateTime.toLocalDate();
                        } catch (DateTimeParseException e2) {
                            System.out.println("날짜 파싱 에러: " + e2.getMessage());
                        }
                    }

                    // endTime 처리
                    if (endTime != null) {
                        try {
                            dateTime = LocalDate.parse(endTime, formatter);
                        } catch (DateTimeParseException e1) {
                            try {
                                OffsetDateTime offsetDateTime = OffsetDateTime.parse(endTime);
                                dateTime = offsetDateTime.toLocalDate();
                            } catch (DateTimeParseException e2) {
                                System.out.println("endTime 파싱 에러: " + e2.getMessage());
                            }
                        }
                    }

                    // 날짜 비교
                    if (dateTime != null && (dateTime.isEqual(today) || dateTime.isAfter(today))) {
                        filteredEvents.add(event); // 이벤트 추가 로직
                        System.out.println("이벤트가 추가됩니다: " + dateTime);
                    }
                }
            }
        }

        return filteredEvents;
    }

    public void addEventsToGoogleCalendar(List<Map<String, Object>> events, Credential credential) throws Exception {
        Calendar service = new Calendar.Builder(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();

        System.out.println(events);

        for (Map<String, Object> event : events) {
            Map<String, Object> properties = (Map<String, Object>) event.get("properties");
            Map<String, Object> dateProperty = (Map<String, Object>) properties.get("날짜");
            Map<String, String> dateDetails = (Map<String, String>) dateProperty.get("date");

            String startTime = dateDetails.get("start");
            ZonedDateTime zonedStartDateTime = (LocalDate.parse(startTime.substring(0, 10))).atStartOfDay(ZoneId.systemDefault());
            String endTime = dateDetails.get("end");

            String memo = "";

            // 시작 시간과 종료 시간을 DateTime 객체로 변환
            DateTime startDateTime;
            DateTime endDateTime;

            // 타이틀 추출
            String title = "";
            Map<String, Object> nameProperty = (Map<String, Object>) properties.get("이름");
            List<Map<String, Object>> titleList = (List<Map<String, Object>>) nameProperty.get("title");
            if (!titleList.isEmpty()) {
                title = (String) titleList.get(0).get("plain_text");
                System.out.println("추출된 내용: " + title);
            } else {
                System.out.println("title 배열이 비어 있습니다.");
            }

            // 태그 추출
            Map<String, Object> tagProperty = (Map<String, Object>) properties.get("태그");
            List<Map<String, Object>> multiSelectList = (List<Map<String, Object>>) tagProperty.get("multi_select");
            String tagName = "";
            if (!multiSelectList.isEmpty()) {
                tagName = (String) multiSelectList.get(0).get("name");
                memo += tagName + ", ";
                System.out.println("추출된 태그 이름: " + tagName);
            } else {
                System.out.println("multi_select 배열이 비어 있습니다.");
            }

            // 메모 추출
            Map<String, Object> memoProperty = (Map<String, Object>) properties.get("메모");
            List<Map<String, Object>> richTextList = (List<Map<String, Object>>) memoProperty.get("rich_text");
            if (richTextList != null && !richTextList.isEmpty()) {
                for (Map<String, Object> textElement : richTextList) {
                    Map<String, Object> text = (Map<String, Object>) textElement.get("text");
                    String plainText = (String) text.get("content");

                    // plain_text 출력
                    System.out.println(plainText);
                    memo += plainText;
                }
            } else {
                System.out.println("메모가 없습니다.");
            }


            if (tagName.equals("공고")) {
                if (startTime != null) {
                    startDateTime = new DateTime(zonedStartDateTime.toInstant().toEpochMilli());
                    endDateTime = new DateTime(zonedStartDateTime.plusDays(1).toInstant().toEpochMilli());
                    String time = startTime.length() == 10 ? "23:59" : startTime.substring(11, 13) + ":" + (startTime.substring(14, 16).equals("00") ? "" : startTime.substring(14, 16));
                    title += " ~" + time;
                } else {
//                    System.out.println("Invalid start time: " + startTime);
                    continue; // 잘못된 경우 건너뛰기
                }
            } else {
                // 시작 시간과 종료 시간을 DateTime 객체로 변환
                if (startTime != null) {
                    if (startTime.contains("T")) { // 날짜와 시간이 모두 있는 경우
                        startDateTime = new DateTime(ZonedDateTime.parse(startTime).withZoneSameInstant(ZoneId.systemDefault()).toInstant().toEpochMilli());
//                        System.out.println("시작날짜 시간 있는 경우 " + startDateTime);
                    } else { // 날짜만 있는 경우
                        startDateTime = new DateTime(zonedStartDateTime.toInstant().toEpochMilli());
//                        System.out.println("시작날짜 시간 없는 경우 " + startDateTime);
                    }
                } else {
//                    System.out.println("Invalid start time: " + startTime);
                    continue; // 잘못된 경우 건너뛰기
                }

                if (endTime != null) {
                    if (endTime.contains("T")) { // 종료 날짜와 시간이 모두 있는 경우
                        endDateTime = new DateTime(ZonedDateTime.parse(endTime).withZoneSameInstant(ZoneId.systemDefault()).toInstant().toEpochMilli());
                    } else { // 종료 날짜만 있는 경우
                        ZonedDateTime zonedEndDateTime = (LocalDate.parse(endTime.substring(0, 10))).plusDays(1).atStartOfDay(ZoneId.systemDefault()).minusSeconds(1);
                        endDateTime = new DateTime(zonedEndDateTime.toInstant().toEpochMilli());
                    }
                } else {
                    // 종료 시간이 없는 경우, 시작 시간을 기반으로 당일의 마지막 시간으로 설정
                    endDateTime = new DateTime(zonedStartDateTime.plusDays(1).toInstant().toEpochMilli());
//                    if (startTime.contains("T")) {
//                        title = ZonedDateTime.parse(startTime).getHour() + ":" + ZonedDateTime.parse(startTime).getMinute() + " " + title;
//                    }
                }
            }


            memo += "\n" + event.get("id");


            // 메모에서 아이디 추출 (마지막 줄)
            String[] memoLines = memo.split("\n");
            String extractedId = memoLines[memoLines.length - 1]; // 마지막 줄이 아이디
            System.out.println("추출된 아이디: " + extractedId);

            // 기존 이벤트 검색 (추출한 아이디로 중복 확인)
            Events existingEvents = service.events().list("primary")
                    .setSingleEvents(true)
                    .setOrderBy("startTime")
                    .setTimeMin(new DateTime(System.currentTimeMillis()))
                    .execute();

            Event existingEvent = null;
            for (Event e : existingEvents.getItems()) {
                if (e.containsKey("description")) {
                    String description = e.get("description").toString();
                    if (description.contains(extractedId)) {
                        existingEvent = e;
                        break;
                    }
                }
            }

//            System.out.println("검색 결과 " + existingEvent);

            // 중복된 이벤트가 있는 경우 업데이트
            if (existingEvent != null) {
//                Event existingEvent = existingEvents.getItems().get(0); // 첫 번째 일치하는 이벤트 가져오기

                // 업데이트할 내용 설정
                existingEvent.setSummary(title) // 제목 업데이트
                        .setDescription(memo) // 설명 업데이트
                        .setStart(new EventDateTime().setDateTime(startDateTime)) // 시작 시간 업데이트
                        .setEnd(new EventDateTime().setDateTime(endDateTime)); // 종료 시간 업데이트

                // 이벤트 업데이트
                service.events().update("primary", existingEvent.getId(), existingEvent).execute();
                System.out.println("이벤트가 업데이트되었습니다: " + existingEvent.getSummary());
            } else {
                // 중복된 이벤트가 없으면 새로운 이벤트를 추가
                Event googleEvent = new Event()
                        .setSummary(title) // 제목 설정
                        .setDescription(memo) // 설명 설정
                        .setStart(new EventDateTime().setDateTime(startDateTime)) // 시작 시간 설정
                        .setEnd(new EventDateTime().setDateTime(endDateTime)); // 종료 시간 설정

                service.events().insert("primary", googleEvent).execute(); // 구글 캘린더에 이벤트 추가
                System.out.println("새로운 이벤트가 추가되었습니다: " + googleEvent.getSummary());
            }
        }
    }


    public void addTestEventToGoogleCalendar(Credential credential) throws Exception {
        Calendar service = new Calendar.Builder(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();

        // 테스트 일정 정보 설정
        Event testEvent = new Event()
                .setSummary("Test Event")
                .setLocation("Location")
                .setDescription("This is a test event.")
                .setStart(new EventDateTime()
                        .setDateTime(DateTime.parseRfc3339(OffsetDateTime.now().plusDays(1).toString()))) // 내일로 설정
                .setEnd(new EventDateTime()
                        .setDateTime(DateTime.parseRfc3339(OffsetDateTime.now().plusDays(1).plusHours(1).toString()))); // 내일 1시간 후로 설정

        // 구글 캘린더에 이벤트 추가
        service.events().insert("primary", testEvent).execute();
    }
}
