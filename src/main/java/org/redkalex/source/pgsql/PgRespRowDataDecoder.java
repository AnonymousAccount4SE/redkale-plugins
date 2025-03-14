/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkalex.source.pgsql;

import java.io.Serializable;
import java.nio.ByteBuffer;
import org.redkale.source.EntityInfo;
import org.redkale.util.*;
import static org.redkalex.source.pgsql.PgClientCodec.*;
import static org.redkalex.source.pgsql.PgPrepareDesc.PgExtendMode.*;

/**
 *
 * @author zhangjx
 */
public class PgRespRowDataDecoder extends PgRespDecoder<PgRowData> {

    public static final PgRespRowDataDecoder instance = new PgRespRowDataDecoder();

    private PgRespRowDataDecoder() {
    }

    @Override
    public byte messageid() {
        return MESSAGE_TYPE_DATA_ROW; // 'D'
    }

    @Override  //RowData 一行数据
    public PgRowData read(PgClientConnection conn, ByteBuffer buffer, final int length, ByteArray array, PgClientRequest request, PgResultSet dataset) {
        PgPrepareDesc prepareDesc = request.getType() == PgClientRequest.REQ_TYPE_EXTEND_QUERY ? conn.getPgPrepareDesc(((PgReqExtended) request).sql) : null;
        if (prepareDesc == null) { //text
            byte[][] byteValues = new byte[buffer.getShort()][];
            for (int i = 0; i < byteValues.length; i++) {
                byteValues[i] = (byte[]) PgsqlFormatter.decodeRowColumnValue(buffer, array, null, buffer.getInt());
            }
            return new PgRowData(byteValues, null);
        }
        PgPrepareDesc.PgExtendMode mode = prepareDesc.mode();
        //binary
        PgColumnFormat[] formats = prepareDesc.resultFormats();
        Attribute[] attrs = prepareDesc.resultAttrs();
        Serializable[] realValues = new Serializable[buffer.getShort()];
        for (int i = 0; i < realValues.length; i++) {
            realValues[i] = formats[i].decoder().decode(buffer, array, attrs[i], buffer.getInt());
        }
        EntityInfo info = request.info;
        if (mode == FIND_ENTITY) {
            dataset.oneEntity = info.getFullEntityValue(realValues);
            return PgRowData.NIL;
        } else if (mode == FINDS_ENTITY) {
            dataset.addEntity(info.getFullEntityValue(realValues));
            return PgRowData.NIL;
        } else if (mode == LISTALL_ENTITY) {
            dataset.addEntity(info.getFullEntityValue(realValues));
            return PgRowData.NIL;
        } else {
            return new PgRowData(null, realValues);
        }
    }

}
