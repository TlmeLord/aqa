import io.qameta.allure.Description;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EndpointE2ETest {
    static final String API_KEY = "qazWSXedc";
    static final String BASE_URL = "http://localhost:8080/endpoint";
    static final String VALID_TOKEN = "1234567890ABCDEF1234567890ABCDEF";

    @BeforeAll
    static void setup() {
        RestAssured.useRelaxedHTTPSValidation();
    }

    @Test
    @Order(1)
    @DisplayName("Позитивный сценарий LOGIN для пользователя")
    @Description("Отправляем корректный токен и действие LOGIN. Ожидаем ответ result=OK.")
    void testLogin() {
        given()
                .contentType(ContentType.URLENC)
                .accept(ContentType.JSON)
                .header("X-Api-Key", API_KEY)
                .formParam("token", VALID_TOKEN)
                .formParam("action", "LOGIN")
        .when()
                .post(BASE_URL)
        .then()
                .statusCode(200)
                .body("result", equalTo("OK"));
    }

    @Test
    @Order(2)
    @DisplayName("Позитивный сценарий ACTION после LOGIN")
    @Description("Выполняем действие ACTION для токена, который прошёл LOGIN. Ожидаем OK.")
    void testActionAfterLogin() {
        given()
                .contentType(ContentType.URLENC)
                .accept(ContentType.JSON)
                .header("X-Api-Key", API_KEY)
                .formParam("token", VALID_TOKEN)
                .formParam("action", "ACTION")
        .when()
                .post(BASE_URL)
        .then()
                .statusCode(200)
                .body("result", equalTo("OK"));
    }

    @Test
    @Order(3)
    @DisplayName("Позитивный сценарий LOGOUT")
    @Description("Выполняем LOGOUT для активного токена. Ожидаем OK.")
    void testLogout() {
        given()
                .contentType(ContentType.URLENC)
                .accept(ContentType.JSON)
                .header("X-Api-Key", API_KEY)
                .formParam("token", VALID_TOKEN)
                .formParam("action", "LOGOUT")
        .when()
                .post(BASE_URL)
        .then()
                .statusCode(200)
                .body("result", equalTo("OK"));
    }

    @Test
    @DisplayName("Неверный токен: токен не прошёл LOGIN")
    @Description("Попытка ACTION с токеном, который не прошёл LOGIN, должна возвращать ERROR.")
    void testActionWithoutLogin() {
        String newToken = "22222222333333334444444455555555"; 
        given()
                .contentType(ContentType.URLENC)
                .accept(ContentType.JSON)
                .header("X-Api-Key", API_KEY)
                .formParam("token", newToken)
                .formParam("action", "ACTION")
        .when()
                .post(BASE_URL)
        .then()
                .statusCode(anyOf(is(400), is(403)))
                .body("result", equalTo("ERROR"))
                .body("message", anyOf(
                    containsStringIgnoringCase("login"),
                    containsStringIgnoringCase("not found"),
                    containsStringIgnoringCase("token")
                ));
    }

    @Test
    @DisplayName("Отсутствует заголовок X-Api-Key")
    @Description("Если не передан X-Api-Key, получаем ошибку.")
    void testMissingApiKey() {
        given()
                .contentType(ContentType.URLENC)
                .accept(ContentType.JSON)
                .formParam("token", VALID_TOKEN)
                .formParam("action", "LOGIN")
        .when()
                .post(BASE_URL)
        .then()
                .statusCode(anyOf(is(401), is(403), is(200)))
                .body("result", equalTo("ERROR"));
    }

    @Test
    @DisplayName("Токен неверной длины")
    @Description("Если токен не 32-символа — получаем ERROR.")
    void testShortToken() {
        given()
                .contentType(ContentType.URLENC)
                .accept(ContentType.JSON)
                .header("X-Api-Key", API_KEY)
                .formParam("token", "ABC123")
                .formParam("action", "LOGIN")
        .when()
                .post(BASE_URL)
        .then()
                .statusCode(anyOf(is(400), is(200)))
                .body("result", equalTo("ERROR"))
                .body("message", containsStringIgnoringCase("token"));
    }

    @Test
    @DisplayName("Неизвестное действие action")
    @Description("Попытка передать неизвестное действие приводит к ERROR.")
    void testUnknownAction() {
        given()
                .contentType(ContentType.URLENC)
                .accept(ContentType.JSON)
                .header("X-Api-Key", API_KEY)
                .formParam("token", VALID_TOKEN)
                .formParam("action", "SOMETHINGELSE")
        .when()
                .post(BASE_URL)
        .then()
                .statusCode(anyOf(is(400), is(200)))
                .body("result", equalTo("ERROR"));
    }

    @Test
    @DisplayName("Повторный LOGOUT после завершения сессии")
    @Description("Если делать LOGOUT повторно для неактивного токена, должен быть ERROR.")
    void testLogoutAfterLogout() {
        // Сначала LOGOUT (чтобы токен точно был неактивен)
        // Может вернуть 200 (если токен был активен) или 403 (если уже был залогаутен)
        given()
                .contentType(ContentType.URLENC)
                .accept(ContentType.JSON)
                .header("X-Api-Key", API_KEY)
                .formParam("token", VALID_TOKEN)
                .formParam("action", "LOGOUT")
        .when()
                .post(BASE_URL)
        .then()
                .statusCode(anyOf(is(200), is(403)));
        // Снова LOGOUT — ожидаем ошибку
        given()
                .contentType(ContentType.URLENC)
                .accept(ContentType.JSON)
                .header("X-Api-Key", API_KEY)
                .formParam("token", VALID_TOKEN)
                .formParam("action", "LOGOUT")
        .when()
                .post(BASE_URL)
        .then()
                .statusCode(anyOf(is(400), is(403)))
                .body("result", equalTo("ERROR"));
    }
}
