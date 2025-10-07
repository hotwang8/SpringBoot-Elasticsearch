USE ACTION_LOG_DB;

INSERT INTO dbo.ACTION_LOG (LOG_JSON, CREATED_AT, UPDATED_AT)
VALUES
(N'{"action":"login","userName":"孫中山","userId":1001,"status":"success","ipAddress":"192.168.1.100","device":"Chrome/Windows"}', '2023-10-15 08:30:45', '2023-10-15 08:30:45'),
(N'{"action":"view_product","userName":"林肯","userId":1002,"productId":5001,"category":"electronics","duration":45}', '2023-10-15 09:12:33', '2023-10-15 09:15:22'),
(N'{"action":"add_to_cart","userName":"甘地","userId":1003,"productId":3045,"quantity":2,"price":599.99}', '2023-10-15 10:05:18', '2023-10-15 10:05:18'),
(N'{"action":"checkout","userName":"居禮夫人","userId":1002,"orderId":8745,"totalAmount":1299.50,"paymentMethod":"credit_card"}', '2023-10-15 11:30:00', '2023-10-15 11:32:15'),
(N'{"action":"search","userName":"愛因斯坦","userId":1005,"keyword":"smartphone","resultCount":42,"filters":{"price":"high-to-low","brand":"Samsung"}}', '2023-10-16 14:22:10', '2023-10-16 14:22:10'),
(N'{"action":"logout","userName":"南丁格爾","userId":1001,"sessionDuration":3600,"status":"normal"}', '2023-10-16 15:45:20', '2023-10-16 15:45:20'),
(N'{"action":"update_profile","userName":"達文西","userId":1004,"fields":["address","phone"],"status":"success"}', '2023-10-17 09:10:35', '2023-10-17 09:12:40'),
(N'{"action":"view_order_history","userName":"莎士比亞","userId":1002,"ordersViewed":[8745,8340,7901],"filterApplied":"last_6_months"}', '2023-10-17 16:05:12', '2023-10-17 16:07:33'),
(N'{"action":"product_review","userName":"貝多芬","userId":1005,"productId":3045,"rating":4.5,"reviewLength":120,"helpful_count":3}', '2023-10-18 11:22:45', '2023-10-19 08:15:30'),
(N'{"action":"api_error","userName":"哥白尼","module":"payment_gateway","errorCode":"API-500","message":"Connection timeout","retryCount":3}', '2023-10-19 13:40:22', '2023-10-19 13:45:18');