-- RETRIEVE A QUESTION HAVING A WORD LIKE
select q.id,w.word,q.uuid,q.created,q.last_accessed,q.study_points,q.study_points_multiplier,q.tag
from main.question q
         inner join main.word w on q.id = w.question_id
where q.id in (select question_id from main.word where word LIKE '%' || ? || '%');