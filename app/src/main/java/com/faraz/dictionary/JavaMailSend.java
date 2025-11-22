package com.faraz.dictionary;

import java.io.File;
import java.util.Date;
import java.util.Properties;

import javax.activation.CommandMap;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.activation.MailcapCommandMap;
import javax.mail.BodyPart;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class JavaMailSend extends javax.mail.Authenticator {
  private final String _port;
  private final String _sport;
  private final String _host;
  private final boolean _auth;
  private final boolean _debuggable;
  private BodyPart attachmentBodyPart;
  private String _user;
  private String _pass;
  private String[] _to;
  private String _from;
  private String _subject;
  private String _body;

  public JavaMailSend() {
    _host = "smtp.gmail.com"; // default smtp server
    _port = "465"; // default smtp port
    _sport = "465"; // default socketfactory port

    _debuggable = false; // debug mode on or off - default off
    _auth = true; // smtp authentication - default on

    // There is something wrong with MailCap, javamail can not find a
    // handler for the multipart/mixed part, so this bit needs to be added.
    MailcapCommandMap mc = (MailcapCommandMap) CommandMap
            .getDefaultCommandMap();
    mc.addMailcap("text/html;; x-java-content-handler=com.sun.mail.handlers.text_html");
    mc.addMailcap("text/xml;; x-java-content-handler=com.sun.mail.handlers.text_xml");
    mc.addMailcap("text/plain;; x-java-content-handler=com.sun.mail.handlers.text_plain");
    mc.addMailcap("multipart/*;; x-java-content-handler=com.sun.mail.handlers.multipart_mixed");
    mc.addMailcap("message/rfc822;; x-java-content-handler=com.sun.mail.handlers.message_rfc822");
    CommandMap.setDefaultCommandMap(mc);
  }

  public JavaMailSend(String user, String pass) {
    this();
    _user = user;
    _pass = pass;
  }

  public boolean send() throws Exception {
    Properties props = _setProperties();

    if (!_user.isEmpty() && !_pass.isEmpty() && _to.length > 0
            && !_from.isEmpty() && !_subject.isEmpty()
            && !_body.isEmpty()) {
      Session session = Session.getInstance(props, this);

      MimeMessage msg = new MimeMessage(session);

      msg.setFrom(new InternetAddress(_from, "Personal Dictionary"));

      InternetAddress[] addressTo = new InternetAddress[_to.length];
      for (int i = 0; i < _to.length; i++) {
        addressTo[i] = new InternetAddress(_to[i]);
      }
      msg.setRecipients(MimeMessage.RecipientType.TO, addressTo);
      msg.setSubject(_subject);
      msg.setSentDate(new Date());
      msg.setHeader("Content-Type", "text/html");
      msg.setHeader("X-Priority", "1");
      Multipart mp = new MimeMultipart();
      MimeBodyPart htmlPart = new MimeBodyPart();
      htmlPart.setContent(_body, "text/html");
      mp.addBodyPart(htmlPart);
      if (attachmentBodyPart != null) {
        mp.addBodyPart(attachmentBodyPart);
      }
      msg.setContent(mp, "text/html");
      // send email
      Transport.send(msg);
      return true;
    } else {
      return false;
    }
  }

  public void addAttachment(String filename) throws Exception {
    attachmentBodyPart = new MimeBodyPart();
    DataSource source = new FileDataSource(filename);
    attachmentBodyPart.setDataHandler(new DataHandler(source));
    attachmentBodyPart.setFileName(filename.substring(filename.lastIndexOf(File.separatorChar) + 1));
  }

  @Override
  public PasswordAuthentication getPasswordAuthentication() {
    return new PasswordAuthentication(_user, _pass);
  }

  private Properties _setProperties() {
    Properties props = new Properties();

    props.put("mail.smtp.host", _host);

    if (_debuggable) {
      props.put("mail.debug", "true");
    }

    if (_auth) {
      props.put("mail.smtp.auth", "true");
    }

    props.put("mail.smtp.port", _port);
    props.put("mail.smtp.socketFactory.port", _sport);
    props.put("mail.smtp.socketFactory.class",
            "javax.net.ssl.SSLSocketFactory");
    props.put("mail.smtp.socketFactory.fallback", "false");

    return props;
  }

  public void setBody(String _body) {
    this._body = _body;
  }

  public void set_to(String[] _to) {
    this._to = _to;
  }

  public void set_from(String _from) {
    this._from = _from;
  }

  public void set_subject(String _subject) {
    this._subject = _subject;
  }
}
