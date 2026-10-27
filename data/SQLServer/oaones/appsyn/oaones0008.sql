IF COL_LENGTH('dbo.uf_oaones_app','vis_role_ids') IS NULL
    BEGIN
        ALTER TABLE dbo.uf_oaones_app ADD vis_role_ids varchar(2000) NULL;
        PRINT 'added uf_oaones_app.vis_role_ids';
    END
ELSE
    PRINT 'uf_oaones_app.vis_role_ids already exists';
GO
