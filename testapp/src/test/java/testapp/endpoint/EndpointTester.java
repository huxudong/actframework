package testapp.endpoint;

import com.alibaba.fastjson.JSON;
import okhttp3.*;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.osgl.$;
import org.osgl.Osgl;
import org.osgl.http.H;
import org.osgl.mvc.result.BadRequest;
import org.osgl.mvc.result.ErrorResult;
import org.osgl.mvc.result.Forbidden;
import org.osgl.mvc.result.NotFound;
import org.osgl.storage.ISObject;
import org.osgl.util.C;
import org.osgl.util.Codec;
import org.osgl.util.E;
import org.osgl.util.S;
import testapp.TestBase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class EndpointTester extends TestBase {

    public static final String END_POINT = "http://localhost:6111";

    private OkHttpClient http;
    private Request.Builder req;
    private Response resp;
    protected ReqBuilder reqBuilder;

    private static Process process;

    @BeforeClass
    public static void bootup() throws Exception {
        if (ping()) {
            return;
        }
        process = new ProcessBuilder(
                "mvn","exec:exec").start();
        InputStream is = process.getInputStream();
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);
        String line;

        while ((line = br.readLine()) != null) {
            System.out.println(line);
            if (line.contains("to start the app")) {
                break;
            }
        }
    }

    @AfterClass
    public static void shutdown() throws Exception {
        if (null != process) {
            shutdownApp();
            process.destroy();
        }
    }

    @Before
    public void setup() {
        http = new OkHttpClient.Builder()
                .connectTimeout(60 * 10, TimeUnit.SECONDS)
                .readTimeout(60 * 10, TimeUnit.SECONDS)
                .writeTimeout(60 * 10, TimeUnit.SECONDS).build();
        req = null;
        resp = null;
        reqBuilder = null;
    }


    private void execute() throws IOException {
        resp = http.newCall(req().build()).execute();
    }

    protected Request.Builder req()  {
        if (null == req) {
            E.illegalStateIf(null == reqBuilder, "Please make sure url(String, Object...) method called first");
            req = reqBuilder.build();
        }
        return req;
    }

    protected Response resp() throws IOException {
        if (null == resp) {
            execute();
        }
        return resp;
    }

    protected void verify(EndPointTestContext context) throws Exception {
        context.applyTo(this);
    }

    protected void verifyAllMethods(String expected, String url, String key, Object val, Object ... otherPairs) throws Exception {
        verifyGet(expected, url, key, val, otherPairs);
        verifyPostFormData(expected, url, key, val, otherPairs);
        verifyPostJsonBody(expected, url, key, val, otherPairs);
    }

    protected void verifyGet(String expected, String url, String key, Object val, Object ... otherPairs) throws Exception {
        setup();
        url(url).get(key, val, otherPairs);
        bodyEq(expected);
    }

    protected void verifyPostFormData(String expected, String url, String key, Object val, Object ... otherPairs) throws Exception {
        setup();
        url(url).post(key, val, otherPairs);
        bodyEq(expected);
    }

    protected void verifyPostJsonBody(String expected, String url, String key, Object val, Object ... otherPairs) throws Exception {
        setup();
        Map<String, Object> params = prepareJsonData(key, val, otherPairs);
        url(url).postJSON(params);
        bodyEq(expected);
    }

    protected ReqBuilder url(String pathTmpl, Object ... args) {
        reqBuilder = new ReqBuilder(pathTmpl, args);
        return reqBuilder;
    }

    protected void bodyContains(String s) throws IOException {
        yes(resp().body().string().contains(s));
    }

    protected void bodyEq(String s) throws IOException {
        final Response resp = resp();
        checkResponseCode(resp);
        eq(s, S.string(resp.body().string()));
    }

    protected void bodyEqIgnoreSpace(String s) throws IOException {
        final Response resp = resp();
        eq(200, resp.code());
        eq(s.trim(), S.string(resp.body().string()).trim());
    }

    protected void bodyEq(Object obj) throws IOException {
        bodyEq(JSON.toJSONString(obj));
    }

    protected void bodyEqIgnoreSpace(Object obj) throws IOException {
        bodyEqIgnoreSpace(JSON.toJSONString(obj));
    }


    protected Map<String, Object> prepareJsonData(String key, Object val, Object ... otherPairs) {
        Map<String, Object> params = C.newMap(key, val);
        Map<String, Object> otherParams = C.map(otherPairs);
        params.putAll(otherParams);
        return params;
    }

    protected Map<String, Object> prepareJsonData(List<$.T2<String, Object>> params) {
        Map<String, Object> map = C.newMap();
        for ($.T2<String, Object> pair : params) {
            String key = pair._1;
            Object val = pair._2;
            if (map.containsKey(key)) {
                List list;
                Object x = map.get(key);
                if (x instanceof List) {
                    list = $.cast(x);
                } else {
                    list = C.newList(x);
                    map.put(key, list);
                }
                list.add(val);
            } else {
                map.put(key, val);
            }
        }
        return map;
    }

    private static void shutdownApp() throws Exception {
        try {
            OkHttpClient http = new OkHttpClient();
            Request req = new ReqBuilder("/shutdown").build().build();
            Response resp = http.newCall(req).execute();
            System.out.println(resp.code());
        } catch (Exception e) {
            // ignore
        }
    }

    private static boolean ping() {
        try {
            OkHttpClient http = new OkHttpClient();
            Request req = new ReqBuilder("/ping").build().build();
            Response resp = http.newCall(req).execute();
            return resp.code() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static String fullUrl(String pathTmpl, Object ... args) {
        String tmpl0 = pathTmpl.startsWith("/") ? pathTmpl : "/" + pathTmpl;
        return S.fmt(END_POINT + tmpl0, args);
    }

    private void checkResponseCode(Response resp) {
        if (resp.code() < 300 && resp.code() > 199) {
            return;
        }
        switch (resp.code()) {
            case HttpURLConnection.HTTP_NOT_FOUND:
                throw NotFound.INSTANCE;
            case HttpURLConnection.HTTP_BAD_REQUEST:
                throw BadRequest.INSTANCE;
            case HttpURLConnection.HTTP_FORBIDDEN:
                throw Forbidden.INSTANCE;
            default:
                throw new ErrorResult(H.Status.of(resp.code()));
        }
    }

    protected static class ReqBuilder {
        StringBuilder sb;
        boolean paramAttached;
        private H.Method method = H.Method.GET;
        private H.Format format = H.Format.JSON;
        private RequestBody body;
        private String postStr;
        private byte[] postBytes;
        private ISObject postAttachment;
        private List<$.T2<String, Object>> postParams = C.newList();

        public ReqBuilder(String pathTmpl, Object ... args) {
            String s = fullUrl(pathTmpl, args);
            sb = S.builder(s);
            paramAttached = s.contains("?");
        }

        public ReqBuilder get() {
            return method(H.Method.GET);
        }

        public ReqBuilder get(String key, Object val, Object ... morePairs) {
            params(key, val, morePairs);
            return get();
        }

        public ReqBuilder get(List<$.T2<String, Object>> pairs) {
            params(pairs);
            return get();
        }

        public ReqBuilder post() {
            return method(H.Method.POST);
        }

        public ReqBuilder post(String key, Object val, Object... morePairs) {
            format(H.Format.FORM_URL_ENCODED);
            post();
            return params(key, val, morePairs);
        }

        public ReqBuilder post(List<$.T2<String, Object>> pairs) {
            format(H.Format.FORM_URL_ENCODED);
            post();
            return params(pairs);
        }

        public ReqBuilder put() {
            return method(H.Method.PUT);
        }

        public ReqBuilder delete() {
            return method(H.Method.DELETE);
        }

        public ReqBuilder method(H.Method method) {
            this.method = method;
            return this;
        }

        public ReqBuilder format(H.Format format) {
            this.format = format;
            return this;
        }

        public ReqBuilder body(RequestBody body) {
            this.body = body;
            return this;
        }

        public ReqBuilder param(String key, Object val) {
            E.illegalArgumentIf(S.blank(key));
            if (method == H.Method.GET) {
                sb.append(paramAttached ? "&" : "?");
                paramAttached = true;
                sb.append(key).append("=").append(Codec.encodeUrl(S.string(val)));
            } else {
                postParams.add($.T2(key, val));
            }
            return this;
        }

        public ReqBuilder params(String key, Object val, Object ... otherPairs) {
            E.illegalArgumentIf(otherPairs.length % 2 != 0);
            param(key, val);
            int len = otherPairs.length;
            for (int i = 0; i < len - 1; i += 2) {
                String key0 = S.string(otherPairs[i]);
                param(key0, otherPairs[i + 1]);
            }
            return this;
        }

        public ReqBuilder params(List<$.T2<String, Object>> pairs) {
            if (null != pairs) {
                for ($.T2<String, Object> pair : pairs) {
                    param(pair._1, pair._2);
                }
            }
            return this;
        }

        public ReqBuilder post(String content) {
            this.post();
            this.postStr = content;
            return this;
        }

        public ReqBuilder postJSON(Object content) {
            this.postParams.clear();
            this.postBytes = null;
            this.format = H.Format.JSON;
            this.post();
            post(JSON.toJSONString(content));
            return this;
        }

        public ReqBuilder post(byte[] content) {
            this.post();
            this.postBytes = content;
            return this;
        }

        public ReqBuilder post(ISObject content) {
            this.post();
            this.postAttachment = content;
            return this;
        }

        public Request.Builder build() {
            Request.Builder builder = new Request.Builder().url(sb.toString());
            switch (method) {
                case GET:
                    return builder.get();
                case POST:
                    return builder.post(body());
                case PUT:
                    return builder.put(body());
                case DELETE:
                    return builder.delete(body());
                case PATCH:
                    return builder.patch(body());
                default:
                    return builder;
            }
        }

        private RequestBody body() {
            if (null == body) {
                body = buildBody();
            }
            return body;
        }

        private MediaType mediaType() {
            return MediaType.parse(format.contentType());
        }

        private RequestBody buildBody() {
            if (format == H.Format.JSON) {
                return buildJsonBody();
            } else {
                return buildFormEncoded();
            }
        }

        private RequestBody buildJsonBody() {
            if (S.notBlank(postStr)) {
                return RequestBody.create(mediaType(), postStr);
            } else {
                return RequestBody.create(mediaType(), JSON.toJSONString(postParams));
            }
        }

        private RequestBody buildFormEncoded() {
            FormBody.Builder builder = new FormBody.Builder();
            for ($.T2<String, Object> entry : postParams) {
                String val = S.string(entry._2);
                if (this.method == H.Method.GET) {
                    val = Codec.encodeUrl(val);
                }
                builder.add(entry._1, val);
            }
            return builder.build();
        }

    }

}
