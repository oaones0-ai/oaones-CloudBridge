/* OAones：移除已废弃的 OA 分部绑定功能（uf_oaones_sub_bind）。 */
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_cnt FROM user_tables WHERE table_name = 'UF_OAONES_SUB_BIND';
    IF v_cnt > 0 THEN
        EXECUTE IMMEDIATE 'DROP TABLE uf_oaones_sub_bind CASCADE CONSTRAINTS';
        DBMS_OUTPUT.PUT_LINE('dropped uf_oaones_sub_bind');
    ELSE
        DBMS_OUTPUT.PUT_LINE('uf_oaones_sub_bind does not exist');
    END IF;
END;
/
