/* Remove the retired OA subcompany binding table. */
IF OBJECT_ID('dbo.uf_oaones_sub_bind','U') IS NOT NULL
    BEGIN
        DROP TABLE dbo.uf_oaones_sub_bind;
        PRINT 'dropped uf_oaones_sub_bind';
    END
ELSE
    PRINT 'uf_oaones_sub_bind does not exist';
GO
