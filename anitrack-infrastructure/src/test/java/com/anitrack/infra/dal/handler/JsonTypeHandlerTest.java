package com.anitrack.infra.dal.handler;

import com.anitrack.domain.anime.model.AnimeTag;
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
}
