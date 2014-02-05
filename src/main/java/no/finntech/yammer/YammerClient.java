/** © Copyright 2013 FINN AS
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Lesser General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Lesser General Public License for more details.
 *
 *   You should have received a copy of the GNU Lesser General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package no.finntech.yammer;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.meterware.httpunit.HttpUnitOptions;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebForm;
import com.meterware.httpunit.WebResponse;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public final class YammerClient implements Closeable {

	/**
	 * Restful Yammer URL for messages api.
	 */
	private static final String YAMMER_API_V1_MESSAGES = "https://www.yammer.com/api/v1/messages";
    private static final String OAUTH_GET_ACCESS_TOKEN_URL = "https://www.yammer.com/dialog/oauth?client_id=%s";
	/**
	 * Yammer URL for getting access token.
	 */
	private static final String OAUTH_ACCESS_TOKEN_URL = "https://www.yammer.com/oauth2/access_token.xml?client_id=%s&client_secret=%s&code=%s";

	private static final String MESSAGE_GROUP_ID_PARAM_NAME = "group_id";
	private static final String MESSAGE_BODY_PARAM_NAME = "body";
    private static final String MESSAGE_TOPIC_PARAM_NAME = "topic";

    private static final Charset UTF8 = Charset.forName("UTF-8");


    private final String accessAuthToken;
    private final CloseableHttpClient httpclient;


    /**
     *
     * @param applicationKey The key of the application registered with Yammer. See http://www.yammer.com/client_applications/new
     * @param applicationSecret The secret of the application registered with Yammer. See http://www.yammer.com/client_applications/new
     * @throws IOException
     */
    public YammerClient(
            final String applicationKey,
            final String username,
            final String password,
            final String applicationSecret) throws IOException {

        httpclient = HttpClientBuilder.create().useSystemProperties().build();
        this.accessAuthToken = getAccessTokenParameters(applicationKey, username, password, applicationSecret);
    }

	public void sendMessage(final String group, final String message, final String... topics) throws IOException {

        HttpPost httpPost = new HttpPost(YAMMER_API_V1_MESSAGES);
        httpPost.addHeader("Authorization", "Bearer " + accessAuthToken);

        List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair(MESSAGE_BODY_PARAM_NAME, message));

        if (group != null && !group.equals("")) {
            nvps.add(new BasicNameValuePair(MESSAGE_GROUP_ID_PARAM_NAME, group));
        }

        for(int i = 0; i < topics.length; ++i) {
            nvps.add(new BasicNameValuePair(MESSAGE_TOPIC_PARAM_NAME + (i+1), topics[i]));
        }

        httpPost.setEntity(new UrlEncodedFormEntity(nvps, UTF8));
        HttpResponse response = httpclient.execute(httpPost);
        if(201 != response.getStatusLine().getStatusCode()) {
            throw new ClientProtocolException("failed to post message to yammer: " + response);
        }
	}

    @Override
    public void close() throws IOException {
        httpclient.close();
    }

	private String getAccessTokenParameters(
            final String applicationKey,
            final String username,
            final String password,
            final String applicationSecret) throws IOException {

        String accessToken = getAccesToken(applicationKey, username, password);
        HttpGet httpGet = new HttpGet(String.format(OAUTH_ACCESS_TOKEN_URL, applicationKey, applicationSecret, accessToken));
        HttpResponse response = httpclient.execute(httpGet);
        if(200 == response.getStatusLine().getStatusCode()) {
            try {
                DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
                Document doc = docBuilder.parse(response.getEntity().getContent());
                Element accessTokenElement = (Element) doc.getDocumentElement().getElementsByTagName("access-token").item(0);
                return accessTokenElement.getElementsByTagName("token").item(0).getTextContent();
            } catch (ParserConfigurationException | SAXException ex) {
                throw new IOException("failed to parse xml response", ex);
            }
        } else {
            throw new IOException("failed request: " + response);
        }
	}

    private String getAccesToken(
            final String applicationKey,
            final String username,
            final String password) throws IOException {

        try {
            HttpUnitOptions.setScriptingEnabled(false);
            WebConversation wc = new WebConversation();
            WebResponse resp = wc.getResponse(String.format(OAUTH_GET_ACCESS_TOKEN_URL, applicationKey));
            WebForm form = findLoginForm(resp.getForms());
            form.setParameter("login", username);
            form.setParameter("password", password);
            resp = form.submit();
            if(!resp.getURL().toString().contains("code=")) {
                resp = resp.getLinkWith("Allow").click();
            }
            return resp.getURL().toString().split("code=")[1];
        } catch (SAXException ex) {
            throw new IOException(ex);
        }
    }

    private WebForm findLoginForm(WebForm[] forms) throws IOException {
        for(WebForm form : forms) {
            if("login-form".equalsIgnoreCase(form.getID())) {
                return form;
            }
        }
        throw new IOException("No login form found on " + OAUTH_GET_ACCESS_TOKEN_URL);
    }
}
