package com.faraz.dictionary;

import java.io.IOException;
import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.BodyPart;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.search.AndTerm;
import javax.mail.search.FlagTerm;
import javax.mail.search.SubjectTerm;

public class JavaMailRead {
  public static String readMail(String email, String password) {
    Properties props = new Properties();
    props.put("mail.smtp.host", "smtp.gmail.com");
    props.put("mail.smtp.port", "587");
    props.put("mail.smtp.auth", "true");
    props.put("mail.smtp.starttls.enable", "true");

    Session session = Session.getInstance(props, new Authenticator() {
      @Override
      protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(email, password);
      }
    });

    // Connect to the email server
    String plainContent = null;
    try (Store store = session.getStore("imaps")) {
      store.connect("imap.gmail.com", email, password);

      try (Folder inbox = store.getFolder("inbox")) {
        inbox.open(Folder.READ_WRITE);
        // Search for unread messages
        FlagTerm unreadFlagTerm = new FlagTerm(new Flags(Flags.Flag.SEEN), false);
        // Search for messages with a specific subject
        SubjectTerm subjectTerm = new SubjectTerm("inmemorydb");
        // Combine the search terms
        AndTerm combinedTerm = new AndTerm(unreadFlagTerm, subjectTerm);
        // Retrieve emails from the inbox
        Message[] messages = inbox.search(combinedTerm);
        for (Message message : messages) {
          try {
            Object content = message.getContent();
            if (content instanceof Multipart multipart) {
              for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                if (bodyPart.getContentType().toLowerCase().startsWith("text/plain")) {
                  plainContent = (String) bodyPart.getContent();
                  System.out.println("Found an all important email.");
                } else if (bodyPart.getContentType().toLowerCase().startsWith("text/html")) {
                  // handle HTML content
                } else {
                  // handle attachement
                }
              }
            } else {
              plainContent = (String) content;
            }
          } catch (IOException | MessagingException e) {
            // handle exception
          }
          message.setFlag(Flags.Flag.SEEN, true);
          message.setFlag(Flags.Flag.DELETED, true);
        }
      } catch (MessagingException e) {
        // handle exception
      }
    } catch (MessagingException e) {
      // handle exception
    }
    return plainContent;
  }
}
