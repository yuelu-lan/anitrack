package com.anitrack.infra.dal.handler;

import com.anitrack.domain.anime.model.AnimeTag;
import com.anitrack.domain.anime.model.Infobox;
import com.anitrack.domain.anime.model.InfoboxItem;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JsonTypeHandlerTest {

    @Test
    void set_and_get_roundtrip() throws SQLException {
        AnimeTagListTypeHandler handler = new AnimeTagListTypeHandler();
        PreparedStatement ps = mock(PreparedStatement.class);

        List<AnimeTag> tags = List.of(AnimeTag.of("科幻", 10));
        handler.setParameter(ps, 1, tags, null);
        verify(ps).setString(eq(1), contains("科幻"));

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("col")).thenReturn("[{\"name\":\"科幻\",\"count\":10}]");
        List<AnimeTag> out = handler.getResult(rs, "col");
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getName()).isEqualTo("科幻");
    }

    @Test
    void null_in_null_out() throws SQLException {
        AnimeTagListTypeHandler handler = new AnimeTagListTypeHandler();
        PreparedStatement ps = mock(PreparedStatement.class);
        handler.setParameter(ps, 1, null, null);
        verify(ps).setNull(eq(1), anyInt());
    }

    @Test
    void infobox_roundtrip_both_forms() throws Exception {
        InfoboxListTypeHandler handler = new InfoboxListTypeHandler();
        List<Infobox> input = List.of(
                Infobox.ofText("中文名", "代号"),
                Infobox.ofItems("别名", List.of(InfoboxItem.of("英文名", "Lelouch"))));

        String json = JsonTypeHandler.MAPPER.writeValueAsString(input);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("col")).thenReturn(json);
        List<Infobox> out = handler.getResult(rs, "col");

        assertThat(out).hasSize(2);

        Infobox textForm = out.get(0);
        assertThat(textForm.getKey()).isEqualTo("中文名");
        assertThat(textForm.getValueText()).isEqualTo("代号");
        assertThat(textForm.getValueItems()).isEmpty();

        Infobox itemsForm = out.get(1);
        assertThat(itemsForm.getKey()).isEqualTo("别名");
        assertThat(itemsForm.getValueText()).isNull();
        assertThat(itemsForm.getValueItems()).hasSize(1);
        assertThat(itemsForm.getValueItems().get(0).getK()).isEqualTo("英文名");
        assertThat(itemsForm.getValueItems().get(0).getV()).isEqualTo("Lelouch");
    }
}
