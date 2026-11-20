package gov.medicaid.pem.notification.infrastructure;

import gov.medicaid.pem.notification.application.NoticePage;
import gov.medicaid.pem.notification.application.NoticePorts;
import gov.medicaid.pem.notification.application.NoticeView;
import gov.medicaid.pem.notification.application.Template;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** notice and notice_template, read side (MAM-60). */
@Repository
public class JdbcNoticeLog implements NoticePorts.NoticeLog {

  private final JdbcTemplate jdbc;

  public JdbcNoticeLog(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public NoticePage find(String npi, int page, int size) {
    String where = npi == null ? "" : " WHERE npi = ?";
    List<Object> args = new ArrayList<>();
    if (npi != null) {
      args.add(npi);
    }
    Long total =
        jdbc.queryForObject("SELECT count(*) FROM notice" + where, Long.class, args.toArray());
    List<Object> pageArgs = new ArrayList<>(args);
    pageArgs.add(size);
    pageArgs.add((long) page * size);
    List<NoticeView> items =
        jdbc.query(
            "SELECT public_id, npi, template_code, channel, status, sent_at FROM notice"
                + where
                + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
            JdbcNoticeLog::row,
            pageArgs.toArray());
    return new NoticePage(items, page, size, total == null ? 0 : total);
  }

  @Override
  public List<Template> templates() {
    return jdbc.query(
        "SELECT code, subject, body FROM notice_template ORDER BY id",
        (rs, n) -> new Template(rs.getString(1), rs.getString(2), rs.getString(3)));
  }

  private static NoticeView row(ResultSet rs, int n) throws SQLException {
    Timestamp sent = rs.getTimestamp("sent_at");
    return new NoticeView(
        rs.getObject("public_id", UUID.class),
        rs.getString("npi"),
        rs.getString("template_code"),
        rs.getString("channel"),
        rs.getString("status"),
        sent == null ? null : sent.toInstant());
  }
}
