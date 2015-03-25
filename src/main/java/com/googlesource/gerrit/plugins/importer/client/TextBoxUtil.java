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

package com.googlesource.gerrit.plugins.importer.client;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;

public class TextBoxUtil {
  public static TextBox addTextBox(Panel p, String label, String infoMsg) {
    return addTextBox(p, label, infoMsg, false);
  }

  public static TextBox addPasswordTextBox(Panel p, String label, String infoMsg) {
    return addTextBox(p, label, infoMsg, true);
  }

  public static TextBox addTextBox(Panel p, String label, String infoMsg, boolean isPassword) {
    HorizontalPanel hp = new HorizontalPanel();
    hp.add(new Label(label));
    Image info = new Image(ImporterPlugin.RESOURCES.info());
    info.setTitle(infoMsg);
    hp.add(info);
    hp.add(new Label(":"));

    Panel vp = new VerticalPanel();
    vp.add(hp);
    TextBox tb = createTextBox(isPassword);
    vp.add(tb);
    p.add(vp);
    return tb;
  }

  private static TextBox createTextBox(boolean isPassword) {
    TextBox tb;
    if (isPassword) {
      tb = new PasswordTextBox() {
        @Override
        public void onBrowserEvent(Event event) {
          super.onBrowserEvent(event);
          handlePaste(this, event);
        }
      };
    } else {
      tb = new TextBox() {
        @Override
        public void onBrowserEvent(Event event) {
          super.onBrowserEvent(event);
          handlePaste(this, event);
        }
      };
    }
    tb.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        event.stopPropagation();
      }
    });
    tb.sinkEvents(Event.ONPASTE);
    tb.setVisibleLength(40);
    return tb;
  }

  private static void handlePaste(final TextBox tb, Event event) {
    if (event.getTypeInt() == Event.ONPASTE) {
      Scheduler.get().scheduleDeferred(new ScheduledCommand() {
        @Override
        public void execute() {
          if (getValue(tb).length() != 0) {
            tb.setEnabled(true);
          }
        }
      });
    }
  }

  public static String getValue(TextBox tb) {
    return tb.getValue().trim();
  }
}
