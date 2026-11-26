import ResourcePage from '../components/ResourcePage';
import { RESOURCE_CONFIGS } from '../config/resources';

export default function Timetable() {
    return <ResourcePage config={RESOURCE_CONFIGS.timetable} />;
}
