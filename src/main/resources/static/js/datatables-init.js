/*
 * 표 검색 · 정렬 공통 설정 (DataTables).
 * 표에 class="js-datatable"을 붙이면 켜진다. 검색과 정렬은 브라우저에서만 처리하며, 서버가 내려 준 행 안에서 동작한다.
 *  - th.no-sort            : 정렬 · 검색에서 뺀다 (버튼이 있는 열)
 *  - td[data-order="값"]    : 화면 문자열과 다른 정렬용 값 (날짜 yyyyMMddHHmm, 숫자, 상태 순서)
 *  - 서버가 정한 처음 정렬은 바꾸지 않는다 (order: [])
 *  - 행이 없을 때는 안내 문구 행(td[colspan])만 있으므로 DataTables를 켜지 않는다
 */
(function ($) {
    'use strict';

    var KOREAN = {
        search: '검색:',
        info: '총 _TOTAL_건',
        infoEmpty: '표시할 항목이 없습니다.',
        infoFiltered: '(전체 _MAX_건 중 걸러짐)',
        zeroRecords: '일치하는 항목이 없습니다.',
        emptyTable: '표시할 항목이 없습니다.',
        paginate: { first: '처음', last: '마지막', next: '다음', previous: '이전' }
    };

    function init($table) {
        if ($table.find('tbody td[colspan]').length > 0) {
            return null;
        }
        // data-order가 있는 칸은 DataTables가 숫자형으로 보고 HTML을 벗기지 않은 채 검색 대상으로 삼는다.
        // 배지(<span class="badge ...">)의 class 이름이 검색에 걸리지 않도록, 화면에 보이는 글자만 data-search로 지정한다.
        $table.find('tbody td[data-order]').each(function () {
            var $td = $(this);
            if (!$td.attr('data-search')) {
                $td.attr('data-search', $td.text().replace(/\s+/g, ' ').trim());
            }
        });
        var noSort = [];
        $table.find('thead th').each(function (index) {
            if ($(this).hasClass('no-sort')) {
                noSort.push(index);
            }
        });
        return $table.DataTable({
            language: KOREAN,
            order: [],
            paging: false,
            lengthChange: false,
            autoWidth: false,
            columnDefs: [{ orderable: false, searchable: false, targets: noSort }]
        });
    }

    $(function () {
        $('table.js-datatable').each(function () {
            init($(this));
        });
    });

    window.initDataTable = init;
})(jQuery);
