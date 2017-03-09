/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.carbon.identity.event.handler.notification.test.osgi;

import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.ops4j.pax.exam.testng.listener.PaxExam;
import org.osgi.framework.BundleContext;
import org.testng.Assert;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import org.wso2.carbon.email.mgt.EmailTemplateManager;
import org.wso2.carbon.email.mgt.exceptions.I18nEmailMgtException;
import org.wso2.carbon.email.mgt.model.EmailTemplate;
import org.wso2.carbon.identity.common.base.event.EventContext;
import org.wso2.carbon.identity.common.base.event.model.Event;
import org.wso2.carbon.identity.common.base.exception.IdentityException;
import org.wso2.carbon.identity.event.EventConstants;
import org.wso2.carbon.identity.event.EventService;
import org.wso2.carbon.identity.event.handler.notification.NotificationConstants;
import org.wso2.carbon.identity.event.handler.notification.email.bean.Notification;
import org.wso2.carbon.identity.event.handler.notification.exception.NotificationHandlerException;
import org.wso2.carbon.identity.event.handler.notification.test.osgi.util.IdentityNotificationHandlerOSGiTestUtils;
import org.wso2.carbon.identity.event.handler.notification.util.NotificationUtil;
import org.wso2.carbon.identity.mgt.RealmService;
import org.wso2.carbon.identity.mgt.User;
import org.wso2.carbon.identity.mgt.bean.UserBean;
import org.wso2.carbon.identity.mgt.claim.Claim;
import org.wso2.carbon.identity.mgt.exception.AuthenticationFailure;
import org.wso2.carbon.identity.mgt.exception.IdentityStoreException;
import org.wso2.carbon.identity.mgt.exception.UserNotFoundException;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.inject.Inject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.PasswordCallback;

import static org.ops4j.pax.exam.CoreOptions.systemProperty;

/**
 * Test class for password recovery.
 */
@Listeners(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class EventHandlerNotificationTest {

    @Inject
    private BundleContext bundleContext;

    @Inject
    private RealmService realmService;

    @Inject EventService eventService;

    @Configuration
    public Option[] createConfiguration() {

        List<Option> optionList = IdentityNotificationHandlerOSGiTestUtils.getDefaultSecurityPAXOptions();

        optionList.add(systemProperty("java.security.auth.login.config").value(Paths
                .get(IdentityNotificationHandlerOSGiTestUtils.getCarbonHome(), "conf", "security", "carbon-jaas.config")
                .toString()));

        return optionList.toArray(new Option[optionList.size()]);
    }

    @Test(groups = "emailTemplateMgt")
    public void testAddUser() throws IdentityStoreException, AuthenticationFailure {
        UserBean userBean = new UserBean();
        List<Claim> claims = Arrays
                .asList(new Claim("http://wso2.org/claims", "http://wso2.org/claims/username", "johnw"),
                        new Claim("http://wso2.org/claims", "http://wso2.org/claims/firstName", "John"),
                        new Claim("http://wso2.org/claims", "http://wso2.org/claims/lastName", "Wick"),
                        new Claim("http://wso2.org/claims", "http://wso2.org/claims/email", "johnw@wso2.com"));
        userBean.setClaims(claims);

        List<Callback> credentialsList = new ArrayList<>();
        PasswordCallback passwordCallback = new PasswordCallback("password", false);
        passwordCallback.setPassword("testpass".toCharArray());
        credentialsList.add(passwordCallback);
        userBean.setCredentials(credentialsList);

        User user = realmService.getIdentityStore().addUser(userBean);

        Assert.assertNotNull(user, "Failed to receive the user.");
        Assert.assertNotNull(user.getUniqueUserId(), "Invalid user unique id.");
    }

    @Test(groups = "emailTemplateMgt")
    public void testGetEmailTemplate() throws I18nEmailMgtException {
        EmailTemplateManager emailTemplateManager = bundleContext
                .getService(bundleContext.getServiceReference(EmailTemplateManager.class));
        Assert.assertNotNull(emailTemplateManager, "Failed to get email template manager service instance");

        EmailTemplate emailTemplate = emailTemplateManager.getEmailTemplate("en_US", "passwordReset");
        Assert.assertNotNull(emailTemplate.getBody(), "Failed to get email template");
    }

    @Test(groups = "emailTemplateMgt")
    public void testInvalidEmailTemplate() {
        EmailTemplateManager emailTemplateManager = bundleContext
                .getService(bundleContext.getServiceReference(EmailTemplateManager.class));
        Assert.assertNotNull(emailTemplateManager, "Failed to get email template manager service instance");

        try {
            EmailTemplate emailTemplate = emailTemplateManager.getEmailTemplate("en_US", "invalidtemplate");
        } catch (I18nEmailMgtException e) {
            Assert.assertEquals("Can not find the email template type: invalidtemplate with locale: en_US",
                    e.getMessage());
        }

    }

    @Test(groups = "emailTemplateMgt")
    public void testBuildNotification()
            throws IdentityStoreException, UserNotFoundException, InterruptedException, NotificationHandlerException {
        Claim claim = new Claim("http://wso2.org/claims", "http://wso2.org/claims/username", "johnw");
        String userUniqueId = realmService.getIdentityStore().getUser(claim).getUniqueUserId();

        Map<String, String> placeHolders = new HashMap<>();
        placeHolders.put("user-unique-id", userUniqueId);
        placeHolders.put("confirmation-code", "123456789");
        placeHolders.put("TEMPLATE_TYPE", "passwordReset");

        String emailTemplateBody = "\nHi johnw,\n \n"
                + "We received a request to change the password on the johnw account associated with this e-mail"
                + " address. \n"
                + "https://localhost:9292/user-portal/recovery/password-reset?confirmation=123456789\n";
        Notification notification = NotificationUtil.buildNotification(placeHolders);

        Assert.assertTrue(notification.getBody().equals(emailTemplateBody),
                "Placeholders are not replaced by given values");
    }

    @Test(groups = "emailTemplateMgt")
    public void testGetSMTPProperties() {
        NotificationUtil.loadProperties();
        Properties properties = NotificationUtil.getProperties();
        Assert.assertEquals("john@gmail.com",
                properties.getProperty(NotificationConstants.SMTPProperty.MAIL_SMTP_FROM));
        Assert.assertEquals("john@gmail.com",
                properties.getProperty(NotificationConstants.SMTPProperty.MAIL_SMTP_USER));
        Assert.assertEquals("xyz", properties.getProperty(NotificationConstants.SMTPProperty.MAIL_SMTP_PASSWORD));
        Assert.assertEquals(5,
                Long.parseLong(properties.getProperty(NotificationConstants.SMTPProperty.MAIL_SEND_AWAITING_TIME)));

    }

    @Test(groups = "emailTemplateMgt")
    public void testInvalidPushNotificationEvent()
            throws IdentityStoreException, UserNotFoundException {
        HashMap<String, Object> properties = new HashMap<>();
        Claim claim = new Claim("http://wso2.org/claims", "http://wso2.org/claims/username", "johnw");
        String userUniqueId = realmService.getIdentityStore().getUser(claim).getUniqueUserId();
        properties.put("user-unique-id", userUniqueId);
        properties.put("confirmation-code", "1234");
        properties.put("TEMPLATE_TYPE", "passwordReset");
        Event identityMgtEvent = new Event(EventConstants.Event.TRIGGER_NOTIFICATION, properties);
        EventContext eventContext = new EventContext();
        NotificationUtil.getProperties().setProperty(NotificationConstants.SMTPProperty.MAIL_SMTP_FROM, "");

        try {
            eventService.pushEvent(identityMgtEvent, eventContext);
        } catch (IdentityException e) {
            Assert.assertEquals("Error occurred in handler: emailSendfor event: TRIGGER_NOTIFICATION\n",
                    e.getMessage());
        }
    }

}
