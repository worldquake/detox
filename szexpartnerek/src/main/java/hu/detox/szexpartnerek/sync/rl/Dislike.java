package hu.detox.szexpartnerek.sync.rl;

import hu.detox.Agent;
import hu.detox.io.CharIOHelper;
import hu.detox.szexpartnerek.Main;
import hu.detox.szexpartnerek.spring.admin.Admin;
import hu.detox.szexpartnerek.sync.Sync;
import hu.detox.szexpartnerek.sync.rl.component.PartnerList;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

import java.io.File;

import static hu.detox.parsers.JSonUtils.OM;

@Component("rlDislike")
@RequiredArgsConstructor
@Import(Http.class)
public class Dislike implements Admin {
    private final Http client;

    @SneakyThrows
    @Override
    public void run() {
        PartnerList plist = hu.detox.Main.ctx().getBean(PartnerList.class);
        if (client.getLogin() == null) return;
        File file = Agent.getFile(getId() + ".txt", FileFilterUtils.fileFileFilter());
        if (file == null) {
            try (var sync = new Sync(client, false, plist)) {
                sync.dataDl(OM.valueToTree(new int[]{39}));
            }
            Main.query("SELECT rowid FROM partner_ext_view" +
                    " WHERE rating<3 AND rowid NOT IN (SELECT partner_id FROM partner_list WHERE tag='Nem tetszenek')", rs -> {
                while (rs.next()) {
                    client.doLikeAction(false, rs.getInt(1));
                }
                return null;
            });
        } else {
            var cio = CharIOHelper.attempt(file);
            IOUtils.readLines(cio.getReader()).forEach(ln -> client.doLikeAction(true, ln));
            cio.close();
        }
    }

    @Override
    public String getId() {
        return "rlDislike";
    }

    @Override
    public String toString() {
        return getId() + ": manages dislike based on low rating";
    }
}
