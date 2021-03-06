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

Gerrit.install(function(self) {
    function onResumeProjectImport(c) {
      var u = c.textfield();
      var p = c.textfield();
      p.type = 'password';
      var f = c.checkbox();
      var b = c.button('Resume',
        {onclick: function(){
          c.call(
            {user: u.value, pass: p.value, force: f.checked},
            function(r) {
              c.hide();
              window.alert('Import for project "'
                + c.project
                + '" was resumed."'),
              Gerrit.go('/admin/projects/' + c.project);
            });
        }});
      c.popup(c.div(
        c.msg('Resume import for project "'
          + c.project
          + '" ?'),
        c.br(),
        c.prependLabel('User:', u),
        c.br(),
        c.prependLabel('Password:', p),
        c.br(),
        c.label(f, 'Force Resume'),
        c.br(),
        c.br(),
        b));
    }
    self.onAction('project', 'import.resume', onResumeProjectImport);
  });
