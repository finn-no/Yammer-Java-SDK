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
	private static final String YAMMER_API_V1_MESSAGES = "https://www.yammer.com/api/v1/messages?access_token=%s";

	/**
	 * Yammer URL for getting access token.
	 */
	private static final String OAUTH_ACCESS_TOKEN_URL = "https://www.yammer.com/oauth2/access_token?client_id=%s&client_secret=%s&code=%s";

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
     * @param accessCode The Yammer access code, needed for using the Yammer API. https://developer.yammer.com/authentication/#a-testtoken
     * @throws IOException
     */
    public YammerClient(
            final String applicationKey,
            final String applicationSecret,
            final String accessCode) throws IOException {

        httpclient = HttpClientBuilder.create().useSystemProperties().build();
        this.accessAuthToken = getAccessTokenParameters(applicationKey, applicationSecret, accessCode);
    }

	public void sendMessage(final String group, final String message, final String... topics) throws IOException {

        HttpPost httpPost = new HttpPost(String.format(YAMMER_API_V1_MESSAGES, accessAuthToken));

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
            final String applicationSecret,
            final String accessToken) throws IOException {

        HttpGet httpGet = new HttpGet(String.format(OAUTH_ACCESS_TOKEN_URL, applicationKey, applicationSecret, accessToken));
        System.err.println("request " + httpGet);
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
}
