/*
 * SonarQube Android Lint Plugin
 * Copyright (C) 2013 SonarSource and Jerome Van Der Linden, Stephane Nicolas, Florian Roncari, Thomas Bores
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.android.lint;

import com.android.tools.lint.checks.BuiltinIssueRegistry;
import com.android.tools.lint.detector.api.Issue;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.profiles.ProfileImporter;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rule.Severity;
import org.sonar.api.rules.Rule;
import org.sonar.api.rules.RuleFinder;
import org.sonar.api.rules.RulePriority;
import org.sonar.api.utils.ValidationMessages;
import org.sonar.plugins.android.lint.AndroidLintProfileExporter.LintProfile;

import java.io.Reader;

import static org.sonar.plugins.android.lint.AndroidLintProfileExporter.LintIssue;

public class AndroidLintProfileImporter extends ProfileImporter {

  private static final Logger LOGGER = LoggerFactory.getLogger(AndroidLintProfileImporter.class);

  private final RuleFinder ruleFinder;

  public AndroidLintProfileImporter(RuleFinder ruleFinder) {
    super(AndroidLintRulesDefinition.REPOSITORY_KEY, AndroidLintRulesDefinition.REPOSITORY_NAME);
    this.ruleFinder = ruleFinder;
    setSupportedLanguages("java", "xml");
  }

  @Override
  public RulesProfile importProfile(Reader reader, ValidationMessages messages) {
    Serializer serializer = new Persister();
    RulesProfile rulesProfile = RulesProfile.create();
    try {
      LintProfile lintProfile = serializer.read(LintProfile.class, reader);
      for (LintIssue lintIssue : lintProfile.issues) {
        Rule rule = ruleFinder.findByKey(RuleKey.of(AndroidLintRulesDefinition.REPOSITORY_KEY, lintIssue.id));
        if (rule != null) {
          Issue issue = new BuiltinIssueRegistry().getIssue(lintIssue.id);
          com.android.tools.lint.detector.api.Severity lintSeverity = getLintSeverity(lintIssue, issue, messages);
          if (!isIgnored(lintSeverity)) {
            int priority = lintIssue.priority != null ? lintIssue.priority : issue.getPriority();
            RulePriority rulePriority = RulePriority.valueOf(getSeverity(lintIssue, lintSeverity, priority));
            rulesProfile.activateRule(rule, rulePriority);
          }
        }
      }
    } catch (Exception e) {
      messages.addErrorText("Android lint profile could not be imported.");
      LOGGER.error("Android lint profile could not be imported.", e);
    }
    return rulesProfile;
  }

  private boolean isIgnored(com.android.tools.lint.detector.api.Severity lintSeverity) {
    return com.android.tools.lint.detector.api.Severity.IGNORE.equals(lintSeverity);
  }

  private String getSeverity(LintIssue lintIssue, com.android.tools.lint.detector.api.Severity lintSeverity, int priority) {
    switch (lintSeverity) {
      case FATAL:
        return Severity.BLOCKER;
      case ERROR:
        if (priority >= 7) {
          return Severity.CRITICAL;
        }
        return Severity.MAJOR;
      case WARNING:
        if (priority >= 7) {
          return Severity.MAJOR;
        }
        return Severity.MINOR;
      case INFORMATIONAL:
        return Severity.INFO;
      case IGNORE:
      default:
        throw new IllegalStateException("An unknown severity has been imported.");
    }
  }

  private com.android.tools.lint.detector.api.Severity getLintSeverity(LintIssue lintIssue, Issue issue, ValidationMessages messages) {
    com.android.tools.lint.detector.api.Severity lintSeverity = null;
    try {
      if (lintIssue.severity != null) {
        lintSeverity = com.android.tools.lint.detector.api.Severity.valueOf(lintIssue.severity.toUpperCase());
      }
    } catch (IllegalArgumentException iae) {
      messages.addWarningText("Could not recognize severity " + lintIssue.severity + " for rule " + lintIssue.id + " default severity is used");
    }
    if (lintSeverity == null) {
      lintSeverity = issue.getDefaultSeverity();
    }
    return lintSeverity;
  }

}