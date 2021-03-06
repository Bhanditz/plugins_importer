// Copyright (C) 2015 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.importer;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.gerrit.extensions.restapi.RawInput;
import com.google.gerrit.server.OutputFormat;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;

public class RestSession extends HttpSession {

  public RestSession(String url, String user, String pass) {
    super(url, user, pass);
  }

  @Override
  public RestResponse get(String endPoint) throws IOException {
    HttpGet get = new HttpGet(url + "/a" + endPoint);
    return new RestResponse(getClient().execute(get));
  }

  public RestResponse put(String endPoint) throws IOException {
    return put(endPoint, null);
  }

  public RestResponse put(String endPoint, Object content) throws IOException {
    HttpPut put = new HttpPut(url + "/a" + endPoint);
    if (content != null) {
      put.addHeader(new BasicHeader("Content-Type", "application/json"));
      put.setEntity(
          new StringEntity(
              OutputFormat.JSON_COMPACT.newGson().toJson(content), Charsets.UTF_8.name()));
    }
    return new RestResponse(getClient().execute(put));
  }

  public RestResponse putRaw(String endPoint, RawInput stream) throws IOException {
    Preconditions.checkNotNull(stream);
    HttpPut put = new HttpPut(url + "/a" + endPoint);
    put.addHeader(new BasicHeader("Content-Type", stream.getContentType()));
    put.setEntity(
        new BufferedHttpEntity(
            new InputStreamEntity(stream.getInputStream(), stream.getContentLength())));
    return new RestResponse(getClient().execute(put));
  }

  public RestResponse post(String endPoint) throws IOException {
    return post(endPoint, null);
  }

  public RestResponse post(String endPoint, Object content) throws IOException {
    HttpPost post = new HttpPost(url + "/a" + endPoint);
    if (content != null) {
      post.addHeader(new BasicHeader("Content-Type", "application/json"));
      post.setEntity(
          new StringEntity(
              OutputFormat.JSON_COMPACT.newGson().toJson(content), Charsets.UTF_8.name()));
    }
    return new RestResponse(getClient().execute(post));
  }

  public RestResponse delete(String endPoint) throws IOException {
    HttpDelete delete = new HttpDelete(url + "/a" + endPoint);
    return new RestResponse(getClient().execute(delete));
  }

  public static RawInput newRawInput(final String content) {
    Preconditions.checkNotNull(content);
    Preconditions.checkArgument(!content.isEmpty());
    return new RawInput() {
      byte bytes[] = content.getBytes(StandardCharsets.UTF_8);

      @Override
      public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(bytes);
      }

      @Override
      public String getContentType() {
        return "application/octet-stream";
      }

      @Override
      public long getContentLength() {
        return bytes.length;
      }
    };
  }
}
